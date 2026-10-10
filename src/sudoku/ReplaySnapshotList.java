/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.*;

/** Append-only storage with fixed-prefix snapshots; shared reads never hold a disk-I/O lock. */
final class ReplaySnapshotList<T> extends AbstractList<T> implements RandomAccess {
    private static final class Buffer<E> {
        final ArrayList<E> values;
        boolean shared;
        Buffer(){values=new ArrayList<>();}
        Buffer(Collection<E> source){values=new ArrayList<>(source);}
    }
    private Buffer<T> buffer=new Buffer<>();
    private int limit=-1;
    private ReplaySnapshotList(Buffer<T> buffer,int limit){this.buffer=buffer;this.limit=limit;}
    ReplaySnapshotList(){}
    boolean samePrefix(ReplaySnapshotList<?> other){return buffer==other.buffer&&size()==other.size();}
    ReplaySnapshotList<T> snapshot(){
        if(limit>=0)return new ReplaySnapshotList<>(buffer,limit);
        synchronized(buffer){buffer.shared=true;return new ReplaySnapshotList<>(buffer,buffer.values.size());}
    }
    @Override public int size(){if(limit>=0)return limit;synchronized(buffer){return buffer.values.size();}}
    @Override public T get(int index){synchronized(buffer){if(index<0||index>=size())throw new IndexOutOfBoundsException();return buffer.values.get(index);}}
    private void detach(boolean replacing){
        synchronized(buffer){
            if(limit>=0||replacing&&buffer.shared){buffer=new Buffer<>(buffer.values.subList(0,size()));limit=-1;}
        }
    }
    @Override public boolean add(T value){detach(false);synchronized(buffer){return buffer.values.add(value);}}
    @Override public boolean addAll(Collection<? extends T> values){detach(false);synchronized(buffer){return buffer.values.addAll(values);}}
    @Override public T set(int index,T value){detach(true);synchronized(buffer){return buffer.values.set(index,value);}}
}
