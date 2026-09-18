package sudoku;
import java.util.*;import java.lang.reflect.*;import java.awt.Color;
import static sudoku.AnnotationMenuKeyProbe.*;
public final class AnnotationReasoningScopeProbe {
 static final String CANDIDATES="159 149 8 79 2 6 457 3 457 7 26 356 4 8 35 9 25 1 259 249 359 1 59 37 6 8 2457 4 29 59 3 7 1 258 256 2568 6 8 1 2 59 59 3 47 47 25 3 7 6 4 8 125 15 9 3 7 4 8 16 2 15 9 56 8 5 2 79 16 479 147 1467 3 19 169 69 5 3 47 2478 247 278";
 static void settle()throws Exception{Thread w=edt(()->{Field x=CurrentReasoningMenu.class.getDeclaredField("worker");x.setAccessible(true);return (Thread)x.get(menu());});w.join(15000);check(!w.isAlive(),"scan timeout");edt(()->null);}
 static Map<SolutionStep,Integer> results()throws Exception{Field x=CurrentReasoningMenu.class.getDeclaredField("masks");x.setAccessible(true);return (Map<SolutionStep,Integer>)x.get(menu());}
 static boolean hidden(SolutionStep s){return s.getType()==SolutionType.HIDDEN_RECTANGLE && s.getCandidatesToDelete().stream().anyMatch(c->c.getIndex()==1&&c.getValue()==9);}
 public static void main(String[] args)throws Exception{try{
 edt(()->{Options.getInstance().setOperationSoundsEnabled(false);f=new MainFrame(null);p=f.getSudokuPanel();StringBuilder values=new StringBuilder();String[] tokens=CANDIDATES.split(" ");check(tokens.length==81,"fixture length");for(String t:tokens)values.append(t.length()==1?t:"0");p.setSudoku(values.toString());for(int c=0;c<81;c++)if(tokens[c].length()>1)for(int d=1;d<=9;d++)if(!tokens[c].contains(""+d))p.getSudoku().delCandidate(c,d);p.setShowCandidates(true);p.setShowHintCellValue(1);p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);f.setVisible(true);return null;});
 Set<Integer> boxes=new TreeSet<Integer>(Arrays.asList(0,1,72,73));SolutionStep target=null;
 for(SolutionStep s:new TechniqueStepCatalog().findAllRawSteps(p.getSudoku().clone(),null))if(hidden(s)){target=s;break;}
 check(target!=null,"native hidden rectangle absent");check(CurrentReasoningMenu.acceptAnnotations(target,p.getSudoku(),boxes,Collections.emptySet(),Collections.emptySet(),Collections.emptySet()),"box index excludes native rectangle");
 for(int mode=0;mode<4;mode++) {final int kind=mode;
 edt(()->{if(menu()!=null)menu().setVisible(false);p.clearBoxReasoningWithUndo();Field cm=SudokuPanel.class.getDeclaredField("coloringMap"),cn=SudokuPanel.class.getDeclaredField("coloringCandidateMap");cm.setAccessible(true);cn.setAccessible(true);((Map<?,?>)cm.get(p)).clear();((Map<?,?>)cn.get(p)).clear();if(kind==0){Field box=SudokuPanel.class.getDeclaredField("boxReasoningGroups");box.setAccessible(true);((java.util.List<SudokuSet>)box.get(p)).get(0).add(0);for(int c:boxes)((java.util.List<SudokuSet>)box.get(p)).get(0).add(c);}else if(kind==1){for(int c:boxes)((Map<Integer,Color>)cm.get(p)).put(c,Color.ORANGE);}else if(kind==2){for(int c:boxes)((Map<Integer,Color>)cn.get(p)).put(c*10+1,Color.ORANGE);}else{Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);UserChain chain=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(9,0),GroupedChainProbe.node(9,1)},false);chain.setActive(true);a.set(p,chain);}f.showCurrentReasoning(true);return null;});settle();
 check(edt(()->results().keySet().stream().anyMatch(AnnotationReasoningScopeProbe::hidden)),"digit filter hid annotation mode "+mode);
 }
 edt(()->{menu().setVisible(false);p.clearUserChainsWithUndo();Field cn=SudokuPanel.class.getDeclaredField("coloringCandidateMap");cn.setAccessible(true);((Map<?,?>)cn.get(p)).clear();f.showCurrentReasoning(true);return null;});settle();
 check(edt(()->results().keySet().stream().noneMatch(AnnotationReasoningScopeProbe::hidden)),"digit-only included multi-digit rectangle");
 System.out.println("User candidate fixture: native r1c2<>9 found for boxes/cell colors/candidate colors/chain despite digit 1; digit-only remains restricted");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
