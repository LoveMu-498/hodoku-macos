package sudoku;
import java.lang.reflect.*;import java.awt.*;import java.util.*;import javax.swing.*;
import static sudoku.AnnotationMenuKeyProbe.*;
public final class MenuHintSelectionProbe {
 static Object read(String name)throws Exception{Field x=SudokuPanel.class.getDeclaredField(name);x.setAccessible(true);return x.get(p);}
 static void hint(int mode)throws Exception{Method x=MainFrame.class.getDeclaredMethod("getHint",int.class);x.setAccessible(true);x.invoke(f,mode);}
 static void enter()throws Exception{Method x=SudokuPanel.class.getDeclaredMethod("handleReasoningEnter");x.setAccessible(true);x.invoke(p);}
 public static void main(String[] args)throws Exception{try{
 edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku(CurrentReasoningProbe.PUZZLE);p.setShowCandidates(true);p.setShowHintCellValue(2);f.setVisible(true);f.showCurrentReasoning(args.length==0);return null;});AnnotationReasoningScopeProbe.settle();
 String before=edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()));
 edt(()->{SolutionStep choice=AnnotationReasoningScopeProbe.results().keySet().iterator().next();menu().confirm(choice,0,1);check(p.getStep()==null&&read("reasoningProposal")==null,"menu Enter drew step");check(read("selectedReasoningHintStep")!=null,"selection not remembered");return null;});
 for(int level=0;level<2;level++){final int mode=level;edt(()->{javax.swing.Timer dismiss=new javax.swing.Timer(150,e->{for(Window w:f.getOwnedWindows())if(w instanceof JDialog&&w.isVisible())w.dispose();});dismiss.start();try{hint(mode);}finally{dismiss.stop();}check(p.getStep()==null,"partial hint drew step");return null;});}
 edt(()->{hint(2);check(p.getStep()!=null,"F12 failed to draw chosen step");Object firstProposal=read("reasoningProposal");hint(2);check(read("reasoningProposal")==firstProposal,"repeated F12 replaced current proof");check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"hint mutated board");enter();return null;});
 long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end&&edt(()->read("reasoningProposal")!=null))Thread.sleep(25);
 edt(()->{check(!before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"Enter after F12 failed apply");p.undo();check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"undo failed");return null;});
 edt(()->{
 UserChain selectedChain=null;SolutionStep selected=null;
 outer:for(int d=1;d<=9;d++)for(int a=0;a<81;a++)for(int b=a+1;b<81;b++){
  UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(d,a),GroupedChainProbe.node(d,b)},true);
  UserChainValidator.Result r=UserChainValidator.validate(p.getSudoku(),c);
  if(!r.steps.isEmpty()){selectedChain=c;selected=r.steps.get(0);break outer;}
 }
 check(selected!=null,"no authored proof fixture");p.setAnnotationTool(AnnotationTool.FREE_CHAIN);selectedChain.setActive(true);
 Field active=SudokuPanel.class.getDeclaredField("activeUserChain");active.setAccessible(true);active.set(p,selectedChain);
 UserChain assembled=UserChainAssembly.assemble(p.currentReasoningChains()).chain;
 f.selectCurrentReasoning(selected,null,assembled,true,0,1);check(p.getStep()==null,"authored menu drew immediately");hint(2);check(p.getStep()!=null,"authored F12 failed");enter();return null;
 });
 end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end&&edt(()->read("reasoningProposal")!=null))Thread.sleep(25);
 edt(()->{check(!before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"authored menu proof failed application");return null;});
 System.out.println("Tab selection stays undrawn; vague/concrete hints stay undrawn; F12 draws, Enter applies and undo restores");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
