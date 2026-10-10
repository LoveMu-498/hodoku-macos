/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Bounded native long text; the HRPL v3 common prefix keeps its original UTF codec. */
final class ReplayTextCodec {
    private static final int MAX_TEXT_BYTES=4*1024*1024;
    private ReplayTextCodec(){}
    static boolean needsExtension(String text){
        long bytes=0;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);bytes+=c>=1&&c<=127?1:c<=2047?2:3;if(bytes>65535)return true;}
        return false;
    }
    static String common(String text,String field){
        return needsExtension(text)?"[Full "+field+" is stored in the HoDoKu Mac extension; this reader cannot display it.]":text;
    }
    static void write(DataOutputStream out,String text)throws IOException{
        byte[] bytes;
        try{ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));bytes=new byte[encoded.remaining()];encoded.get(bytes);}
        catch(CharacterCodingException failure){throw new IOException("Invalid Unicode in replay text",failure);}
        if(bytes.length>MAX_TEXT_BYTES)throw new IOException("Replay text contains "+bytes.length+" bytes; maximum is "+MAX_TEXT_BYTES+" bytes");
        out.writeInt(bytes.length);out.write(bytes);
    }
    static String read(DataInputStream in)throws IOException{
        byte[] bytes=new byte[ReplayAnnotations.count(in,MAX_TEXT_BYTES)];in.readFully(bytes);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException failure){throw new IOException("Invalid UTF-8 in replay text",failure);}
    }
}
