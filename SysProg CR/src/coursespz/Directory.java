package coursespz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Directory extends Entity implements FileTable {
    String name;
    Directory parent;
    List<Entity> dirElements = new ArrayList<>();

    public Directory(int id, String name, Directory parent) {
        super(id);
        this.name = name;
        this.parent = parent;
    }

    void add(Entity element) {
        dirElements.add(element);
    }

    void remove(Entity element) {
        dirElements.remove(element);
    }

    Directory getDirByName(String name) {
        return dirElements.stream()
                .filter(entity -> entity instanceof Directory dir && dir.name.equals(name))
                .findFirst()
                .map(entity -> (Directory) entity)
                .get();
    }

    SymLink getLinkByName(String name) {
        return dirElements.stream()
                .filter(entity -> entity instanceof SymLink link && link.name.equals(name))
                .findFirst()
                .map(entity -> (SymLink) entity)
                .get();
    }


    boolean hasDir(String name) {
        return dirElements.stream()
                .anyMatch(entity -> entity instanceof Directory dir && dir.name.equals(name));
    }

    boolean hasFile(String name) {
        return dirElements.stream()
                .anyMatch(entity -> entity instanceof File file && file.hasLink(name));
    }

    boolean hasLink(String name) {
        return dirElements.stream()
                .anyMatch(entity -> entity instanceof SymLink link && link.name.equals(name));
    }

    boolean hasElement(String name) {
        return !hasLink(name) && !hasFile(name) && !hasDir(name);
    }

    String getAbsPath() {
        Directory curDir = this;
        List<String> list = new ArrayList<>();
        while (curDir.parent != null) {
            list.add(curDir.name);
            curDir = curDir.parent;
        }
        Collections.reverse(list);
        return "/" + String.join("/", list);
    }

    @Override
    public String toString() {
        return "Directory{id=" + id + '}';
    }
}
