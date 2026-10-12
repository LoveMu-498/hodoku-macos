/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.Color;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
/** Seeds/asserts actual MainFrame session state for the isolated packaged-launcher native quit test. */
public final class DoodleHypothesisSessionProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static final String PUZZLE="530070000600195000098000060800060003400803001700020006060000280000419005000080079";
 @SuppressWarnings("unchecked") static List<DoodleStroke> ink(SudokuPanel p)throws Exception{Field f=SudokuPanel.class.getDeclaredField("doodleStrokes");f.setAccessible(true);return (List<DoodleStroke>)f.get(p);}
 static void mark(SudokuPanel p,int atom,int kind,int group)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod("toggleCandidateHypothesisMark",int.class,int.class,int.class,int.class,Color.class);m.setAccessible(true);m.invoke(p,atom/10,atom%10,kind,group,group==0?Color.RED:Color.BLUE);}
 static String validatedMode(String[] args) {
  if(args.length!=1||!("seed".equals(args[0])||"verify".equals(args[0])))
   throw new IllegalArgumentException("Usage: DoodleHypothesisSessionProbe seed|verify (exactly one mode)");
  String isolated=System.getProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
  if(isolated==null||isolated.trim().isEmpty())
   throw new IllegalArgumentException("An explicit isolated -Dhodoku.data.dir is required; real user data must not be used");
  Path data=Paths.get(isolated).normalize();
  Path defaultData=Paths.get(System.getProperty("user.home"),"Library","Application Support","HoDoKu").toAbsolutePath().normalize();
  if(!data.isAbsolute()||data.getParent()==null||data.equals(defaultData))
   throw new IllegalArgumentException("hodoku.data.dir must be an absolute isolated test directory, not the default user data directory");
  return args[0];
 }
 public static void main(String[]args)throws Exception{
  final String mode;
  try{mode=validatedMode(args);}catch(IllegalArgumentException invalid){System.err.println("REFUSED: "+invalid.getMessage());System.exit(2);return;}
  final Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{MainFrame frame=null;try{
   frame=new MainFrame(null);SudokuPanel p=frame.getSudokuPanel();
   if(mode.equals("seed")){
    frame.setPuzzle(PUZZLE);p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);
    List<Integer> atoms=new ArrayList<Integer>();for(int c=0;c<81;c++)for(int d=1;d<=9;d++)if(p.getSudoku().getValue(c)==0&&p.getSudoku().isCandidate(c,d))atoms.add(c*10+d);
    int shared=atoms.get(0);mark(p,shared,2,0);mark(p,shared,2,1);
    p.getCellZoomPanel().selectPaletteGroup(2);p.toggleDoodleHypothesisEntry();mark(p,atoms.get(1),1,2);mark(p,atoms.get(2),1,2);p.finishDoodleHypothesisEntry();
    List<DoodleStroke> marks=ink(p);for(DoodleStroke s:marks)if(s.getAnchorCell()==shared/10&&s.getAnchorDigit()==shared%10&&s.getThoughtGroup()==0){s.setConclusionOutlined(true);s.setConclusionSourceMask(3);}
    Method save=MainFrame.class.getDeclaredMethod("saveApplicationState");save.setAccessible(true);save.invoke(frame);
   }else{
    check(PUZZLE.equals(PuzzleHistoryEntry.normalizeClues(p.getSudokuString(ClipboardMode.CLUES_ONLY))),"session puzzle changed");
    List<DoodleStroke> marks=ink(p);check(marks.size()==4,"session actual marks not restored: "+marks.size());
    long starts=marks.stream().filter(DoodleStroke::isHypothesisStart).count();check(starts==4,"start metadata lost");
    List<DoodleStroke> ors=new ArrayList<DoodleStroke>();for(DoodleStroke s:marks)if(s.getHypothesisGroupKind()==1)ors.add(s);
    check(ors.size()==2&&ors.get(0).getHypothesisConditionId()>0&&ors.get(0).getHypothesisConditionId()==ors.get(1).getHypothesisConditionId(),"group OR metadata lost");
    DoodleStroke chosen=marks.stream().filter(s->s.isConclusionOutlined()).findFirst().orElse(null);
    check(chosen!=null&&chosen.getConclusionSourceMask()==3,"compound selection lost");
    check(p.getDoodleMarkSourceMask(chosen.getAnchorCell(),chosen.getAnchorDigit(),2)==3,"actual colors not recomputed");
    p.setAnnotationTool(AnnotationTool.DOODLE);p.undoCurrentAnnotation();check(ink(p).size()==3,"persisted undo not usable");p.redoCurrentAnnotation();check(ink(p).size()==4,"persisted redo not usable");
    // The assertion frame opens the live recovery controller too; close cleanly
    // so the following native app never treats this fixture as a crashed attempt.
    Method save=MainFrame.class.getDeclaredMethod("saveApplicationState");save.setAccessible(true);save.invoke(frame);
   }
  }catch(Throwable t){failure[0]=t;}finally{if(frame!=null)frame.dispose();}});
  if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.out.println("PASS "+mode+": MainFrame session marks, group identity, compound selection and undo/redo");System.exit(0);
 }
}
