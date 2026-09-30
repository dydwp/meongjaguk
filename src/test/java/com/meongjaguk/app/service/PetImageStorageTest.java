package com.meongjaguk.app.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetImageStorageTest {

    private final PetImageStorage storage = new PetImageStorage();
    private final List<String> savedUrls = new ArrayList<>();

    /** 테스트 중 저장된 사진은 항상 지움 */
    @AfterEach
    void cleanUp() {
        savedUrls.forEach(storage::deleteAfterCommit);
    }

    @Test
    void noImageMeansNoProfilePhoto() {
        assertNull(storage.save(null));
        assertNull(storage.save(new MockMultipartFile("image", new byte[0])));
    }

    @Test
    void pngIsSavedAndCanBeDeleted() throws IOException {
        String url = save(image("png"));

        assertTrue(url.matches("/images/pets/[0-9a-f-]{36}\\.png"));
        Path file = storage.getDirectory().resolve(Path.of(url).getFileName());
        assertTrue(Files.exists(file));

        storage.deleteAfterCommit(url); // 트랜잭션 밖에서는 바로 삭제
        assertFalse(Files.exists(file));
    }

    @Test
    void jpegIsSavedAsJpg() throws IOException {
        assertTrue(save(image("jpg")).endsWith(".jpg"));
    }

    @Test
    void nonImageFileIsRejected() {
        MockMultipartFile text = new MockMultipartFile("image", "evil.png", "image/png", "not an image".getBytes());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> storage.save(text));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void gifIsRejected() throws IOException {
        MockMultipartFile gif = new MockMultipartFile("image", "dog.gif", "image/gif", image("gif"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> storage.save(gif));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void fileOver5MbIsRejected() {
        MockMultipartFile big = new MockMultipartFile("image", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> storage.save(big));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, e.getStatusCode());
    }

    @Test
    void deleteIgnoresEmptyAndOutsidePaths() {
        storage.deleteAfterCommit(null);
        storage.deleteAfterCommit(" ");
        // 경로 조작이 있어도 파일 이름만 사용하므로 사진 폴더 밖 파일은 건드리지 않음
        storage.deleteAfterCommit("/images/pets/../../../../pom.xml");
        assertTrue(Files.exists(Path.of("pom.xml")));
    }

    private String save(byte[] bytes) {
        String url = storage.save(new MockMultipartFile("image", "dog", "application/octet-stream", bytes));
        savedUrls.add(url);
        return url;
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
