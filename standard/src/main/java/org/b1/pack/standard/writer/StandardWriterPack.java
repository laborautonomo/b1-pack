/*
 * Copyright 2012 b1.org
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

package org.b1.pack.standard.writer;

import com.google.common.collect.Lists;
import org.b1.pack.api.writer.WriterContent;
import org.b1.pack.api.writer.WriterEntry;
import org.b1.pack.api.writer.WriterPack;
import org.b1.pack.api.writer.WriterProvider;
import org.b1.pack.standard.common.Numbers;
import org.b1.pack.standard.common.PbRecordPointer;

import java.io.IOException;
import java.util.List;
import java.util.WeakHashMap;

import static com.google.common.base.Preconditions.checkArgument;

class StandardWriterPack extends WriterPack {

    private final WeakHashMap<WriterEntry, WriterObject> objectMap = new WeakHashMap<WriterEntry, WriterObject>();
    private final List<WriterObject> objectList = Lists.newArrayList();
    private final ArchiveWriter writer;
    private long objectCount;
    private boolean catalogMode;
    private PbRecordPointer nextCatalogPointer;

    public StandardWriterPack(WriterProvider provider) {
        writer = new ArchiveWriter(provider);
    }

    @Override
    public void addFolder(WriterEntry entry) throws IOException {
        addObject(entry, new WriterFolder(getNewId(entry), getParent(entry), entry));
    }

    @Override
    public void addFile(WriterEntry entry, WriterContent content) throws IOException {
        addObject(entry, new WriterFile(getNewId(entry), getParent(entry), entry, content));
    }

    @Override
    public void flush() throws IOException {
        flush(true);
    }

    public void complete() throws IOException {
        writer.setObjectCount(objectCount);
        flush(false);
        writer.setCompressible(false);
        initNextCatalogPointer();
        Numbers.writeLong(null, writer);
        writer.complete();
    }

    public void close() throws IOException {
        writer.close();
    }

    private long getNewId(WriterEntry entry) {
        checkArgument(!objectMap.containsKey(entry), "Duplicate entry");
        return ++objectCount;
    }

    private WriterFolder getParent(WriterEntry entry) {
        WriterEntry parent = entry.getParent();
        if (parent == null) return null;
        WriterObject result = objectMap.get(parent);
        if (result instanceof WriterFolder) {
            return (WriterFolder) result;
        }
        throw new IllegalArgumentException(result == null ? "The parent is unknown" : "The parent is not a folder");
    }

    private void addObject(WriterEntry entry, WriterObject object) throws IOException {
        objectMap.put(entry, object);
        objectList.add(object);
        if (entry.isImmediate()) {
            setContentMode();
            object.saveCompleteRecord(writer);
        }
    }

    private void flush(boolean intermediate) throws IOException {
        if (intermediate && objectList.isEmpty()) {
            return;
        }
        if (writer.isSeekable()) {
            writer.setCompressible(false);
            saveCatalogRecords();
            saveCompleteRecords();
        } else {
            saveCompleteRecords();
            if (intermediate) return;
            writer.setCompressible(true);
            saveCatalogRecords();
        }
        objectList.clear();
        writer.flush();
    }

    private void saveCatalogRecords() throws IOException {
        setCatalogMode();
        for (WriterObject object : objectList) {
            object.saveCatalogRecord(writer);
        }
    }

    private void saveCompleteRecords() throws IOException {
        setContentMode();
        for (WriterObject object : objectList) {
            object.saveCompleteRecord(writer);
        }
    }

    private void setCatalogMode() throws IOException {
        writer.saveCatalogPoiner();
        initNextCatalogPointer();
        catalogMode = true;
    }

    private void setContentMode() throws IOException {
        if (catalogMode && nextCatalogPointer == null) {
            nextCatalogPointer = writer.createEmptyPointer();
            writer.write(nextCatalogPointer);
        }
        catalogMode = false;
    }

    private void initNextCatalogPointer() throws IOException {
        if (nextCatalogPointer != null) {
            nextCatalogPointer.init(writer.getCurrentPointer());
            nextCatalogPointer = null;
        }
    }
}