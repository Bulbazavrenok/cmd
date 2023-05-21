package coursespz;

public class FilePart implements FileTable {
    File file;
    int startIndex;
    int endIndex;
    String dataPart;

    public FilePart(File file, int startIndex, int endIndex) {
        this.file = file;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.dataPart = file.data.substring(startIndex, endIndex);
    }

    @Override
    public String toString() {
        return "FilePart{id=" + file.id + "\\";
    }
}
