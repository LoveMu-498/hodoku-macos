package sudoku;
import java.awt.Color;
import java.beans.*;
import java.io.*;

/** Every readable pair is derived from a single group and one orientation bit. */
public final class PairedPaletteInvariantProbe {
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void pair(Options o,AnnotationPaletteOwner owner,int group,boolean flipped){
        Color[] c=o.getColoringColors();
        require(o.getAnnotationPrimaryColor(owner).equals(c[group*2+(flipped?1:0)]),"primary "+owner);
        require(o.getAnnotationSecondaryColor(owner).equals(c[group*2+(flipped?0:1)]),"secondary "+owner);
    }
    public static void main(String[] args)throws Exception{
        Options o=new Options();o.initializeAnnotationPalettePreferences(null,null);Color[] c=o.getColoringColors();
        for(AnnotationPaletteOwner owner:AnnotationPaletteOwner.values()){
            for(int slot=0;slot<12;slot++){
                o.setAnnotationPrimaryColor(owner,c[slot]);pair(o,owner,slot/2,owner.isSecondarySupported()&&slot%2==1);
                if(owner.isSecondarySupported()){
                    o.setAnnotationSecondaryColor(owner,c[slot]);pair(o,owner,slot/2,slot%2==0);
                    o.swapAnnotationPaletteColors(owner);pair(o,owner,slot/2,slot%2!=0);
                    o.setAnnotationPaletteGroup(owner,(slot/2+1)%6);pair(o,owner,(slot/2+1)%6,slot%2!=0);
                }
            }
        }
        AnnotationPalettePreferences legacy=new AnnotationPalettePreferences();
        legacy.getDoodle().setPrimarySlot(3);legacy.getDoodle().setSecondarySlot(10);
        legacy.getCandidateColoring().setPrimarySlot(2);legacy.getCandidateColoring().setSecondarySlot(6);
        o.setAnnotationPalettePreferences(legacy);pair(o,AnnotationPaletteOwner.DOODLE,1,true);pair(o,AnnotationPaletteOwner.CANDIDATE_COLORING,1,false);
        legacy.getCellColoring().setPrimarySlot(-1);legacy.getCellColoring().setPrimaryCustom(c[8]);
        legacy.getCellColoring().setSecondaryCustom(c[2]);pair(o,AnnotationPaletteOwner.CELL_COLORING,4,false);
        o.setAnnotationPrimaryColor(AnnotationPaletteOwner.DOODLE,new Color(118,80,209));pair(o,AnnotationPaletteOwner.DOODLE,1,false);
        o.swapAnnotationPaletteColors(AnnotationPaletteOwner.DOODLE);
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        try(XMLEncoder encoder=new XMLEncoder(data)){encoder.setExceptionListener(e->{throw new AssertionError(e);});encoder.writeObject(o.getAnnotationPalettePreferences());}
        AnnotationPalettePreferences decoded;
        try(XMLDecoder decoder=new XMLDecoder(new ByteArrayInputStream(data.toByteArray()))){decoded=(AnnotationPalettePreferences)decoder.readObject();}
        Options loaded=new Options();loaded.setAnnotationPalettePreferences(decoded);
        pair(loaded,AnnotationPaletteOwner.DOODLE,1,true);pair(loaded,AnnotationPaletteOwner.CANDIDATE_COLORING,1,false);
        // New canonical fields take precedence over redundant legacy XML fields.
        decoded.getDoodle().setSecondarySlot(10);pair(loaded,AnnotationPaletteOwner.DOODLE,1,true);
        System.out.println("PASS: all owners/all slots, atomic primary/secondary changes, group+swap, mixed legacy recovery, custom nearest pair, XML round-trip");
    }
}
