package sudoku;
import java.awt.*;
import java.lang.reflect.*;
import javax.swing.*;
import static sudoku.AnnotationMenuKeyProbe.*;
/** Loading -> results -> empty -> results must resize both Swing content and native host. */
public final class ReasoningPopupSizeProbe {
 static void settle()throws Exception{Thread w=edt(()->{Field x=CurrentReasoningMenu.class.getDeclaredField("worker");x.setAccessible(true);return (Thread)x.get(menu());});w.join(15000);check(!w.isAlive(),"search timeout");edt(()->null);}
 public static void main(String[] args)throws Exception{try{
 edt(()->{Options.getInstance().setOperationSoundsEnabled(false);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku("7.8.495............34.5..7..5..7...13..8.6..96...9..3..7..8.42............543.1.6");p.setShowCandidates(true);p.setShowHintCellValue(2);f.setVisible(true);f.showCurrentReasoning();return null;});settle();
 int full=edt(()->menu().getHeight());
 edt(()->{radio(menu().modeHeader(),"reasoningFilter2").doClick();return null;});edt(()->null);settle();
 int empty=edt(()->{check(menu().isVisible(),"empty closed popup");check(menu().selector()==null,"expected empty result");Window host=SwingUtilities.getWindowAncestor(menu());check(host.getHeight()<240,"native empty host too tall: "+host.getHeight());java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(menu().getWidth(),menu().getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
 java.awt.Graphics2D g=image.createGraphics();menu().printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",new java.io.File("/tmp/hodoku-compact-release/empty-popup.png"));return menu().getHeight();});
 check(empty<240&&empty<full,"empty did not compact");
 edt(()->{radio(menu().modeHeader(),"reasoningFilter0").doClick();return null;});edt(()->null);settle();
 edt(()->{check(menu().selector()!=null&&menu().getHeight()>empty,"results did not expand again");return null;});
 for(String name:new String[]{"Glass","Tink"})try(javax.sound.sampled.AudioInputStream audio=javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.File("/System/Library/Sounds/"+name+".aiff"))){check(audio.getFrameLength()>0,"empty sound");}
 System.out.println("Native popup full="+full+", empty="+empty+", restored; feedback sounds decode");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
