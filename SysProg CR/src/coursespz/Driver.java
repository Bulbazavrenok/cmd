package coursespz;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sysProg.Disk.*;

public class Driver {
    static int id = 2;
    static Directory cwd;
    static int descriptor = 0;
    static int limitLinkJump = 5;

    static {
        cwd = new Directory(1, "/", null);
        blockFileTable[getStartIndex(1)] = cwd;
    }

    static void ls() {
        cwd.dirElements.forEach(System.out::println);
    }

    static void fileStat(String id) {
        Optional<File> optionalFile = getFilesFromDiskSpace()
                .stream()
                .filter(file -> Integer.parseInt(id) == file.descriptor)
                .findFirst();
        if (optionalFile.isPresent()) System.out.format("File with descriptor %s:\n%s\n", id, optionalFile.get());
        else System.out.format("File with descriptor %s is not found!\n", id);
    }

    static void createFile(String filename, String data) {
        if (filename.startsWith("/")) {
            String[] split = filename.split("/");
            Directory dir = jumpToLink(String.join("/", Arrays.asList(Arrays.copyOf(split, split.length - 1))));
            if (dir != null) {
                createFile(split[split.length - 1], data, dir);
            } else System.out.println("No such directory!");
        } else createFile(filename, data, cwd);
        System.out.println("File is created!");
    }

    private static void createFile(String filename, String data, Directory dir) {
        if (dir.hasFile(filename)) {
            System.out.format("File with name '%s' has already exist!\n", filename);
            return;
        }
        File file = new File(id++, filename, data);
        dir.add(file);
        int startIndex = getStartIndex(file.size);
        if (startIndex == -1) {
            System.out.println("Run out of space!");
            return;
        }
        int index = 0;
        for (int i = startIndex; i < (startIndex + file.size); i++) {
            blockFileTable[i] = new FilePart(file, index, Math.min(file.data.length(), index + 4));
            index += 4;
        }
    }

    static void open(String fileName) {
        if (cwd.hasLink(fileName)) {
            String[] split = getSymLinkByNameFromDiskSpace(fileName).pathName.split("/");
            if (limitLinkJump-- != 0) {
                open(split[split.length - 1]);
            } else {
                limitLinkJump = 5;
                System.out.println("No such file or link!");
            }
        } else if (isFile(fileName)) {
            String[] newFileName = new String[]{fileName};
            if (fileName.startsWith("/")) {
                String[] split = fileName.split("/");
                newFileName[0] = split[split.length - 1];
            }
            getFilesFromDiskSpace()
                    .stream()
                    .filter(file -> file.hasLink(newFileName[0]))
                    .findFirst()
                    .ifPresent(file -> {
                        file.descriptor = ++descriptor;
                        System.out.format("File data: %s\n", file.data);
                    });
        } else System.out.println("No such file or link!");
    }

    static void close(String fd) {
        getFilesFromDiskSpace()
                .stream()
                .filter(file -> file.descriptor.equals(Integer.parseInt(fd)))
                .findFirst()
                .ifPresent(file -> {
                    file.descriptor = null;
                    descriptor--;
                    System.out.format("File '%s' is closed\n", file.id);
                });
    }

    static void read(String fd, String shift) {
        getFilesFromDiskSpace()
                .stream()
                .filter(file -> file.descriptor.equals(Integer.parseInt(fd)))
                .findFirst()
                .ifPresent(file -> {
                    int shiftInt = Integer.parseInt(shift);
                    if (shiftInt > file.data.length()) System.out.println("Shift is larger then data length!");
                    else System.out.format("File data with shift %s: %s\n", shift,
                            file.data.substring(shiftInt));

                });
    }

    static void write(String fd, String shift, String data) {
        getFilesFromDiskSpace()
                .stream()
                .filter(file -> file.descriptor.equals(Integer.parseInt(fd)))
                .findFirst()
                .ifPresent(file -> {
                    int shiftInt = Integer.parseInt(shift);
                    if (shiftInt > file.data.length()) System.out.println("Shift is larger then data length!");
                    else {
                        String newData = file.data.substring(0, shiftInt) + data;
                        if (newData.length() > file.data.length()) {
                            System.out.println("New string more then the old one. Increase size using truncate");
                        } else {
                            file.data = newData + " ".repeat(file.data.length() - newData.length());
                            for (FileTable fileTable : blockFileTable) {
                                if (fileTable instanceof FilePart fp && fp.file.equals(file)) {
                                    fp.dataPart = file.data.substring(fp.startIndex, fp.endIndex);
                                }
                            }
                            System.out.format("New file data: %s\n", file.data);
                        }
                    }
                });
    }

    static void link(String fileName, String linkName) {
        Stream<File> fileStream = cwd.dirElements
                .stream()
                .filter(fsEntity -> fsEntity instanceof File file && file.hasLink(fileName))
                .map(fsEntity -> (File) fsEntity);
        if (fileStream.count() > 1) {
            System.out.println("Hard link with this name exists!");
        } else {
            fileStream
                    .findFirst()
                    .ifPresent(file -> {
                        HardLink link = new HardLink(id++, file, linkName);
                        file.links.add(link);
                        cwd.add(link);
                        blockFileTable[getStartIndex(1)] = link;
                        System.out.format("Link %s to file '%s' is created!\n", linkName, fileName);
                    });
        }
    }

    static void unlink(String linkName) {
        cwd.dirElements
                .stream()
                .filter(fsEntity -> fsEntity instanceof File file && file.hasLink(linkName))
                .map(fsEntity -> (File) fsEntity)
                .findFirst()
                .ifPresent(file -> {
                    HardLink link = file.getLink(linkName);
                    file.links.remove(link);
                    for (int i = 0; i < blockFileTable.length; i++) {
                        if (blockFileTable[i] instanceof HardLink hl && link.id == hl.id) {
                            blockFileTable[i] = null;
                        }
                    }
                    cwd.remove(link);
                    System.out.format("Link '%s' is deleted!\n", linkName);
                    if (file.links.isEmpty()) {
                        for (int i = 0; i < blockFileTable.length; i++) {
                            if (blockFileTable[i] instanceof FilePart fp && fp.file.id == file.id)
                                blockFileTable[i] = null;
                        }
                        cwd.remove(file);
                        System.out.format("File '%s' is deleted!\n", file.id);
                    }
                });
    }

    static void mkDir(String dirName) {
        if (cwd.hasElement(dirName)) {
            Directory newDir = new Directory(id++, dirName, cwd);
            cwd.add(newDir);
            blockFileTable[getStartIndex(1)] = newDir;
            System.out.format("Directory '%s' is created!\n", dirName);
        } else System.out.format("Directory with name '%s' exists\n", dirName);
    }

    static void rmDir(String dirName) {
        if (cwd.hasDir(dirName)) {
            Directory dir = cwd.getDirByName(dirName);
            cwd.remove(dir);
            System.out.format("Directory '%s' is removed!\n", dirName);
        } else System.out.format("Directory with name '%s' does not exist\n", dirName);
    }

    static void cd(String dirName) {
        if ("..".equals(dirName)) {
            if (cwd.parent != null) {
                cwd = cwd.parent;
            }
        }
        else if ("~".equals(dirName)) {
            cwd = getRootDir(cwd);
        }
        else {
            if (dirName.startsWith("/")) {
                Directory dir = jumpToLink(dirName);
                if (dir != null) cwd = dir;
            } else if (cwd.hasDir(dirName)) {
                cwd = cwd.getDirByName(dirName);
            } else if (cwd.hasLink(dirName)) {
                SymLink symLink = cwd.getLinkByName(dirName);
                if (symLink.pathName.startsWith("/")) {
                    Directory dir = jumpToLink(symLink.pathName);
                    if (dir != null) cwd = dir;
                } else if (cwd.hasDir(symLink.pathName)) {
                    cwd = cwd.getDirByName(symLink.pathName);
                } else System.out.println("Directory does not exist!");
            } else System.out.println("Directory does not exist!");
        }
    }

    private static Directory jumpToLink(String name) {
        String[] dirs = name.substring(1).split("/");
        Directory tempDir = cwd;
        for (int i = 0; i < dirs.length; i++) {
            if (tempDir.hasDir(dirs[i])) {
                tempDir = tempDir.getDirByName(dirs[i]);
            } else if (i == (dirs.length - 1) && tempDir.hasLink(dirs[i]) && limitLinkJump-- != 0) {
                cd(dirs[i]);
            } else {
                limitLinkJump = 5;
                System.out.println("No such file or directory!");
            }
        }
        if (limitLinkJump != 0) return tempDir;
        return null;
    }

    static void symLink(String fileOrDirName, String linkName) {
        if (cwd.hasElement(linkName)) {
            SymLink symLink = new SymLink(id++, linkName, fileOrDirName);
            blockFileTable[getStartIndex(1)] = symLink;
            cwd.add(symLink);
            System.out.format("Sym link '%s' is created!\n", linkName);
        } else System.out.println("Link, dir or file with this name exists!");
    }

    private static Directory getRootDir(Directory dir) {
        if (dir.parent == null) return dir;
        return getRootDir(dir.parent);
    }

    private static Set<File> getFilesFromDiskSpace() {
        return Arrays.stream(blockFileTable)
                .filter(iEntityPart -> iEntityPart instanceof FilePart)
                .map(iEntityPart -> ((FilePart) iEntityPart).file)
                .collect(Collectors.toSet());
    }

    private static SymLink getSymLinkByNameFromDiskSpace(String name) {
        return Arrays.stream(blockFileTable)
                .filter(iEntityPart -> iEntityPart instanceof SymLink sl && sl.name.equals(name))
                .findFirst()
                .map(iEntityPart -> (SymLink) iEntityPart)
                .get();
    }

    private static boolean isFile(String name) {
        return Arrays.stream(blockFileTable)
                .anyMatch(iEntityPart -> iEntityPart instanceof FilePart fp && fp.file.hasLink(name));
    }

}
