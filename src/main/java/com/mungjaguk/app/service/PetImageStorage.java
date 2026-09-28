package com.mungjaguk.app.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PetImageStorage {
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private final Path directory = Path.of("src/main/resources/static/images/pets")
            .toAbsolutePath().normalize();

    public Path getDirectory() {
        return directory;
    }

    public String save(MultipartFile image) {
        if (image == null || image.isEmpty()) return null;
        if (image.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }

        Path destination = null;
        try {
            String extension = imageExtension(image);
            Files.createDirectories(directory);
            destination = directory.resolve(UUID.randomUUID() + "." + extension);
            try (InputStream input = image.getInputStream()) {
                Files.copy(input, destination);
            }

            Path savedFile = destination;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) delete(savedFile);
                    }
                });
            }
            return "/images/pets/" + destination.getFileName();
        } catch (IOException exception) {
            if (destination != null) delete(destination);
            throw new IllegalStateException("반려견 사진 저장 실패", exception);
        }
    }

    private String imageExtension(MultipartFile image) throws IOException {
        try (InputStream input = image.getInputStream();
             ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                }
                reader.setInput(stream);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > 40_000_000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                }
                reader.read(0);
                return format.equalsIgnoreCase("JPEG") ? "jpg" : "png";
            } finally {
                reader.dispose();
            }
        }
    }

    private void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            org.slf4j.LoggerFactory.getLogger(PetImageStorage.class)
                    .warn("반려견 사진 정리 실패: {}", file, exception);
        }
    }
}
