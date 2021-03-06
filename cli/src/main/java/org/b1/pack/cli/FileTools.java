/*
 * Copyright 2011 b1.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.b1.pack.cli;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.b1.pack.api.builder.Writable;
import org.b1.pack.api.common.FolderContent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class FileTools {

    public static void saveToFile(Writable writable, File file) throws IOException {
        File tempFile = createTempFile(file);
        FileOutputStream stream = new FileOutputStream(tempFile);
        try {
            writable.writeTo(stream, 0, writable.getSize());
        } finally {
            stream.close();
        }
        Files.move(tempFile, file);
    }

    public static File createTempFile(File file) throws IOException {
        File absoluteFile = file.getAbsoluteFile();
        File parentFile = absoluteFile.getParentFile();
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                throw new IOException("Cannot create: " + parentFile);
            }
        }
        return File.createTempFile(absoluteFile.getName() + "--", null, parentFile);
    }

    public static Set<FsObject> getFsObjects(List<String> names) {
        Map<List<String>, FsObject> map = createRootMap(names);
        Set<FsObject> result = Sets.newLinkedHashSet();
        for (FsObject fsObject : map.values()) {
            addPrecedingObjects(result, fsObject, map);
            addObjectWithChildren(result, fsObject);
        }
        return result;
    }

    public static Map<List<String>, FsObject> createRootMap(List<String> names) {
        Map<List<String>, FsObject> map = Maps.newLinkedHashMap();
        for (String name : names.isEmpty() ? Collections.singleton(".") : names) {
            File file = new File(name);
            List<String> path = getPath(file);
            Preconditions.checkState(map.put(path, new FsObject(file, path)) == null, "Duplicate path: %s", path);
        }
        return map;
    }

    public static List<String> getPath(File file) {
        LinkedList<String> result = Lists.newLinkedList();
        do {
            String name = file.getName();
            if (name.isEmpty() || name.equals(".") || name.equals("..")) {
                return result;
            }
            result.addFirst(name);
            file = file.getParentFile();
        } while (file != null);
        return result;
    }

    private static void addPrecedingObjects(Set<FsObject> result, FsObject fsObject, Map<List<String>, FsObject> map) {
        List<String> path = fsObject.getPath();
        for (int i = 1; i < path.size(); i++) {
            List<String> otherPath = path.subList(0, i);
            FsObject otherObject = map.get(otherPath);
            if (otherObject != null) {
                result.add(otherObject);
            }
        }
    }

    private static void addObjectWithChildren(Set<FsObject> result, FsObject fsObject) {
        if (!fsObject.getPath().isEmpty()) {
            result.add(fsObject);
        }
        File[] childen = fsObject.getFile().listFiles();
        if (childen != null) {
            for (File child : childen) {
                addObjectWithChildren(result, new FsObject(child, asList(fsObject.getPath(), child.getName())));
            }
        }
    }

    private static <T> List<T> asList(List<T> list, T tail) {
        List<T> result = new ArrayList<T>(list.size() + 1);
        result.addAll(list);
        result.add(tail);
        return result;
    }

    public static File getOutputFolder(ArgSet argSet) {
        String outputDirectory = argSet.getOutputDirectory();
        if (outputDirectory == null) return null;
        File result = new File(outputDirectory);
        Preconditions.checkArgument(result.isDirectory(), "Output directory not found: %s", result);
        return result;
    }

    public static FolderContent createFolderContent(List<String> names) {
        return new FsFolderContent(names.isEmpty() ? Collections.singletonList(".") : names);
    }

    public static void setLastModified(File file, Long time) {
        Preconditions.checkState(time == null || file.setLastModified(time), "Cannot set time: %s", file);
    }
}
