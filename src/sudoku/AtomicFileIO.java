/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.*;
import java.security.*;
import java.util.*;

/** Small same-directory atomic replacement helper with one recoverable backup. */
final class AtomicFileIO {
    private static final class Proof {
        final Object format;final byte[] digest;
        Proof(Object format,byte[] digest){this.format=format;this.digest=digest;}
    }
    private static final Map<String,Proof> PROOFS=new LinkedHashMap<String,Proof>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Proof> entry){return size()>128;}
    };
    private static String key(File file){return file.toPath().toAbsolutePath().normalize().toString();}
    private static Proof proof(File file,Object format){synchronized(PROOFS){Proof p=PROOFS.get(key(file));return p!=null&&p.format==format?p:null;}}
    /** Digest of the exact bytes accepted by the format reader or atomically committed by this writer. */
    static byte[] validatedDigest(File file,Object format){Proof p=proof(file,format);return p==null?null:p.digest.clone();}
    private static void remember(File file,Object format,byte[] digest){if(format!=null)synchronized(PROOFS){PROOFS.put(key(file),new Proof(format,digest));}}
    static final class CheckingInput extends DigestInputStream {
        CheckingInput(File file)throws IOException{super(new FileInputStream(file),hash());}
        private static MessageDigest hash()throws IOException{try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}}
    }
    static void rememberValid(File file,Object format,CheckingInput input){remember(file,format,input.getMessageDigest().digest());}
    /** Copy and hash in one streaming pass, with bounded 64 KiB working memory. */
    private static byte[] digest(File source,File copy)throws IOException{
        MessageDigest hash;try{hash=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}
        try(InputStream in=new BufferedInputStream(new FileInputStream(source));OutputStream out=copy==null?new OutputStream(){public void write(int b){}}:new BufferedOutputStream(new FileOutputStream(copy))){
            byte[] bytes=new byte[65536];for(int n;(n=in.read(bytes))!=-1;){hash.update(bytes,0,n);if(copy!=null)out.write(bytes,0,n);}
        }
        return hash.digest();
    }

	interface FileAction {
		void run(File file) throws Exception;
	}

	private AtomicFileIO() {
	}

	static void write(File target, FileAction writer, FileAction validator) throws IOException {
        write(target,writer,validator,null);
    }

    static void write(File target, FileAction writer, FileAction validator,Object format) throws IOException {
        SaveTrace.io("file.total",target.toPath(),()->writeRaw(target,writer,validator,format));
    }
    private static void writeRaw(File target, FileAction writer, FileAction validator,Object format) throws IOException {
		File parent = target.getAbsoluteFile().getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
			throw new IOException("Unable to create directory: " + parent);
		}

		File temporary = File.createTempFile(target.getName() + ".", ".tmp", parent);
        IOException failure = null;
		try {
            SaveTrace.checked("file.encode_write",target.toPath(),()->writer.run(temporary));
            SaveTrace.checked("file.validate_new",target.toPath(),()->validator.run(temporary));
            Proof newProof=format==null?null:proof(temporary,format);
            byte[] newDigest=format==null?null:newProof!=null?newProof.digest:SaveTrace.ioValue("file.new_digest",target.toPath(),()->digest(temporary,null));

			File backup = backupFor(target);
			if (target.isFile()) {
                File backupTemporary=File.createTempFile(target.getName()+".backup.",".tmp",parent);
                IOException backupFailure=null;
                try {
                    byte[] copied=SaveTrace.ioValue("file.backup.copy_hash",target.toPath(),()->digest(target,backupTemporary));Proof known=format==null?null:proof(target,format);
                    boolean valid=known!=null&&MessageDigest.isEqual(known.digest,copied);
                    if(!valid)try{SaveTrace.checked("file.backup.validate",target.toPath(),()->validator.run(backupTemporary));valid=true;}catch(Exception invalid){SaveTrace.event("file.backup.keep_previous",target.toString());}
                    else SaveTrace.event("file.backup.digest_proof",target.toString());
                    if(valid)SaveTrace.io("file.backup.replace",target.toPath(),()->replace(backupTemporary,backup));
                } catch(IOException ex){backupFailure=ex;throw ex;}
                finally {try{Files.deleteIfExists(backupTemporary.toPath());}catch(IOException cleanup){if(backupFailure!=null)backupFailure.addSuppressed(cleanup);else throw cleanup;}}
			}

            SaveTrace.io("file.replace",target.toPath(),()->replace(temporary,target));remember(target,format,newDigest);
		} catch (Exception ex) {
			failure = ex instanceof IOException ? (IOException) ex : new IOException("Unable to write " + target, ex);
            throw failure;
		} finally {
            try { Files.deleteIfExists(temporary.toPath()); }
            catch (IOException cleanup) { if (failure != null) failure.addSuppressed(cleanup); else throw cleanup; }
		}
	}
    private static void replace(File source,File target)throws IOException{
        try{Files.move(source.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException unsupported){Files.move(source.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }

	static File backupFor(File target) {
		return new File(target.getPath() + ".bak");
	}
}
