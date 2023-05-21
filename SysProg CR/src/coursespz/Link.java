package coursespz;

public abstract class Link extends Entity implements FileTable {
    String name;

    public Link(int id, String name) {
        super(id);
        this.name = name;
    }
}
