package sudoku;
import java.util.*;import java.lang.reflect.*;
import static sudoku.GroupedChainTransactionProbe.*;
public final class SwordfishBoxReasoningProbe {
 static final String CANDIDATES="1 8 5 6 2 3 9 7 4 23 7 6 4 5 9 1 8 23 2349 234 23 8 7 1 256 356 235 3568 36 378 579 1 68 4 2 35789 24568 246 278 579 3 2468 567 569 1 234568 1 9 57 46 2468 567 356 3578 2368 236 238 1 9 46 257 45 257 26 5 1 3 46 7 8 49 29 7 9 4 2 8 5 3 1 6";
 static final Set<Integer> BOXES=new TreeSet<Integer>(Arrays.asList(19,20,24,37,38,55,56,60));
 static void boxes()throws Exception{Field x=SudokuPanel.class.getDeclaredField("boxReasoningGroups");x.setAccessible(true);java.util.List<SudokuSet> groups=(java.util.List<SudokuSet>)x.get(p);for(int c:BOXES)groups.get(0).add(c);}
 public static void main(String[] args)throws Exception{try{
 edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();String[] t=CANDIDATES.split(" ");StringBuilder v=new StringBuilder();for(String s:t)v.append(s.length()==1?s:"0");p.setSudoku(v.toString());for(int c=0;c<81;c++)if(t[c].length()>1)for(int d=1;d<=9;d++)if(!t[c].contains(""+d))p.getSudoku().delCandidate(c,d);p.setShowCandidates(true);p.setShowHintCellValue(2);p.setAnnotationTool(AnnotationTool.BOX_SELECTION);boxes();return null;});
 String before=edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()));
 edt(()->{p.selectReasoningHint(new SolutionStep(SolutionType.HIDDEN_RECTANGLE),null,null,false);check(p.getStep()==null,"unrendered selection drew");return null;});
 edt(()->{call("handleReasoningEnter");return null;});await(true);
 edt(()->{check(p.getStep().getType()==SolutionType.SWORDFISH,"not swordfish");check(ReasoningStepIndex.from(p.getStep(),p.getSudoku()).premiseCells.equals(BOXES),"wrong proof orientation");check(p.getStep().getCandidatesToDelete().size()==6,"not six deletions");check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"preview mutated board");call("handleReasoningEnter");return null;});await(false);
 edt(()->{for(int c:new int[]{18,26,36,41,54,62})check(!p.getSudoku().isCandidate(c,2),"missed deletion "+c);p.undo();check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"undo failed");boxes();p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);f.setVisible(true);f.showCurrentReasoning(true);return null;});
 Field m=MainFrame.class.getDeclaredField("currentReasoningMenu");m.setAccessible(true);CurrentReasoningMenu menu=edt(()->(CurrentReasoningMenu)m.get(f));Field w=CurrentReasoningMenu.class.getDeclaredField("worker");w.setAccessible(true);((Thread)w.get(menu)).join(15000);edt(()->null);
 edt(()->{Field mask=CurrentReasoningMenu.class.getDeclaredField("masks");mask.setAccessible(true);SolutionStep target=null;for(SolutionStep s:((Map<SolutionStep,Integer>)mask.get(menu)).keySet())if(s.getType()==SolutionType.SWORDFISH&&ReasoningStepIndex.from(s,p.getSudoku()).premiseCells.equals(BOXES))target=s;check(target!=null,"Tab omitted authored fish");menu.confirm(target,0,1);check(p.getStep()==null,"menu drew without F12");Method h=MainFrame.class.getDeclaredMethod("getHint",int.class);h.setAccessible(true);h.invoke(f,2);check(p.getStep()!=null,"F12 missing");call("handleReasoningEnter");return null;});await(false);
 edt(()->{for(int c:new int[]{18,26,36,41,54,62})check(!p.getSudoku().isCandidate(c,2),"menu apply missing");check(p.getBoxReasoningFootprint().size()==8,"menu consumed boxes");return null;});
 System.out.println("User swordfish: direct Enter preview/apply/undo and Tab select/F12/apply retain native c237 r357 proof and six deletions");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
