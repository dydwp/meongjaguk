from pathlib import Path
from xml.sax import make_parser
from xml.sax.handler import ContentHandler
from xml.sax.saxutils import XMLGenerator

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data" / "seoul_walk.graphml"
TARGET = ROOT / "data" / "seoul_walk_runtime.graphml"

KEEP = {
    "graph": {"crs", "name", "simplified"},
    "node": {"x", "y", "street_count"},
    "edge": {"length", "highway", "osmid", "oneway", "reversed"},
}

## data/seoul_walk_runtime.graphml 용량 줄이는 클래스
class SlimGraphML(ContentHandler):
    """Copy only the GraphML attributes needed by the route API."""

    def __init__(self, output):
        super().__init__()
        self.writer = XMLGenerator(output, encoding="utf-8")
        self.kept_keys = set()
        self.skip_depth = 0

    def startDocument(self):
        self.writer.startDocument()

    def endDocument(self):
        self.writer.endDocument()

    def startElement(self, name, attrs):
        if self.skip_depth:
            self.skip_depth += 1
            return

        if name == "key":
            group = attrs.get("for")
            attribute = attrs.get("attr.name")

            if attribute not in KEEP.get(group, set()):
                self.skip_depth = 1
                return

            self.kept_keys.add(attrs["id"])

        elif name == "data" and attrs.get("key") not in self.kept_keys:
            self.skip_depth = 1
            return

        self.writer.startElement(name, attrs)

    def endElement(self, name):
        if self.skip_depth:
            self.skip_depth -= 1
            return

        self.writer.endElement(name)

    def characters(self, content):
        if not self.skip_depth:
            self.writer.characters(content)


def slim_graphml(source: Path, target: Path) -> None:
    """Stream a large GraphML file into a smaller runtime copy."""
    if source.resolve() == target.resolve():
        raise ValueError("Source and target must be different files")

    temporary = target.with_name(target.name + ".tmp")
    parser = make_parser()

    try:
        with source.open("rb") as source_file, temporary.open(
            "w", encoding="utf-8", buffering=1024 * 1024
        ) as target_file:
            parser.setContentHandler(SlimGraphML(target_file))
            parser.parse(source_file)

        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    print(f"Reading {SOURCE}", flush=True)
    slim_graphml(SOURCE, TARGET)
    size_mib = TARGET.stat().st_size / (1024 * 1024)
    print(f"Saved {TARGET} ({size_mib:.1f} MiB)")
