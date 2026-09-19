package sudoku;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Real native branch action/confirmation plus exact board, raw geometry and portable provenance checks. */
public final class ReplayBranchProbe {
 static MainFrame f;static SudokuPanel p;static ReplayController c;
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void edt(Runnable a)throws Exception{SwingUtilities.invokeAndWait(a);}
 static Object field(String name){try{Field f=SudokuPanel.class.getDeclaredField(name);f.setAccessible(true);return f.get(p);}catch(Exception e){throw new RuntimeException(e);}}
 static JOptionPane pane(Container root){for(Component child:root.getComponents()){if(child instanceof JOptionPane)return (JOptionPane)child;if(child instanceof Container){JOptionPane found=pane((Container)child);if(found!=null)return found;}}return null;}
 static void confirm(int response){javax.swing.Timer timer=new javax.swing.Timer(120,e->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible()){JOptionPane found=pane((Container)w);if(found!=null){found.setValue(response);return;}}throw new AssertionError("branch confirmation missing");});timer.setRepeats(false);timer.start();}
 static JButton branchButton(){for(Component component:c.viewer().actionBar().getComponents())if(component instanceof JButton&&((JButton)component).getText().equals(ReplayText.text("branch")))return (JButton)component;throw new AssertionError("branch action missing");}
 public static void main(String[]args)throws Exception{
  Path data=Files.createTempDirectory("replay-branch-");System.setProperty("hodoku.data.dir",data.toString());
  try{
   edt(()->{f=new MainFrame(null);f.setVisible(true);p=f.getSudokuPanel();c=f.getReplayController();});
   Sudoku2 base=new Sudoku2();base.clearSudoku();for(int cell=2;cell<9;cell++)base.delCandidate(cell,1);for(int row=2;row<9;row++)base.delCandidate(row*9,1);
   UserChain chain=new UserChain();chain.setActive(true);chain.setClosed(true);for(int cell:new int[]{0,1,9})chain.getNodes().add(new UserChainNode(cell,1,Color.BLUE));chain.getStrongRelations().addAll(Arrays.asList(true,false,true));
   UserChainValidator.Result result=UserChainValidator.preview(base,chain);require(!result.steps.isEmpty(),"actual native fixture has no proof");
   List<SudokuSet> boxes=new ArrayList<SudokuSet>();for(int i=0;i<6;i++)boxes.add(new SudokuSet());boxes.get(0).add(0);boxes.get(0).add(1);boxes.get(1).add(1);boxes.get(1).add(9);
   UserChain grouped=new UserChain();UserChainNode gn=new UserChainNode(27,2,Color.GREEN);gn.setGroupCells(new int[]{27,28});grouped.getNodes().add(gn);
   byte[] raw=ReplayEvidence.input("FREE_CHAIN",boxes,Arrays.asList(grouped,chain));byte[] evidence=ReplayEvidence.result(raw,ReplayProof.encode(result.steps.get(0)),result,"NATIVE_MATCH");
   ReplayBoard before=new ReplayBoard(base);Sudoku2 filled=base.clone();filled.setCell(0,1,false,true);ReplayBoard after=new ReplayBoard(filled);
   ReplaySession source=new ReplaySession(before,1000);source.elapsedMillis=300;source.retained=true;source.endedAt=2000;source.append(Arrays.asList(new ReplayFrame(1,1100,100,"authored-input","raw",before,evidence),new ReplayFrame(1,1200,200,"authored-result","result",before,evidence),new ReplayFrame(1,1300,300,"authored-apply","apply",after,evidence)));
   Path sourceFile=c.directory().resolve(source.id+".hrep");ReplayStore.write(sourceFile,source);byte[] originalBytes=Files.readAllBytes(sourceFile);
   String oldId=c.session().id;ReplayBoard oldBoard=new ReplayBoard(p.getSudoku());
   edt(()->{c.openViewer(source);c.viewer().showFrame(2);confirm(JOptionPane.CANCEL_OPTION);branchButton().doClick();require(c.isViewing()&&c.session().id.equals(oldId)&&new ReplayBoard(p.getSudoku()).equals(oldBoard),"cancel mutated current attempt");confirm(JOptionPane.OK_OPTION);branchButton().doClick();require(!c.isViewing(),"confirmed branch did not return to edit");});
   require(new ReplayBoard(p.getSudoku()).equals(before),"proof-result frame applied historic conclusion");require(p.getStep()==null&&field("reasoningProposal")==null,"historic proof remains executable");require(c.session().sourceReplayId.equals(source.id)&&c.session().sourceFrameIndex==2,"source identity missing");require(c.session().initialAnnotations().length>0,"new recording depends on old raw data");
   edt(()->{require(p.getBoxReasoningGroupsSnapshot().get(0).equals(boxes.get(0))&&p.getBoxReasoningGroupsSnapshot().get(1).equals(boxes.get(1)),"group identity lost");boolean foundGroup=false;for(UserChain current:p.currentReasoningChains())for(UserChainNode node:current.getNodes())if(node.getGroupCells()!=null&&node.getGroupCells().length==2)foundGroup=true;require(p.currentReasoningChains().size()==2&&foundGroup,"grouped chain shape lost");try{Field menu=MainFrame.class.getDeclaredField("undoMenuItem");menu.setAccessible(true);require(!((JMenuItem)menu.get(f)).isEnabled(),"branch undo menu stale");}catch(Exception e){throw new RuntimeException(e);}ReplayBoard start=new ReplayBoard(p.getSudoku());p.undo();require(new ReplayBoard(p.getSudoku()).equals(start),"new attempt inherited undo");try{Method enter=SudokuPanel.class.getDeclaredMethod("handleReasoningEnter");enter.setAccessible(true);enter.invoke(p);}catch(Exception e){throw new RuntimeException(e);}require(new ReplayBoard(p.getSudoku()).equals(start),"first Enter executed historical proof");});
   // Move source file away; the new record must still export/import and display its initial raw geometry.
   Files.move(sourceFile,data.resolve("source-removed.hrep"));Path exported=data.resolve("independent.hrep");ReplayFiles.exportFile(exported,c.session(),c.directory());ReplaySession imported=ReplayFiles.importFile(exported);require(imported.sourceReplayId.equals(source.id)&&imported.initialAnnotations().length>0,"portable provenance missing");
   edt(()->{c.openViewer(imported);c.viewer().showFrame(0);require(!c.viewer().boardPanel().getBoxReasoningGroupsSnapshot().get(0).isEmpty(),"initial replay lost boxes without source file");c.closeViewer();});
   for(int selected:new int[]{1,3}){edt(()->{try{c.openViewer(source);c.viewer().showFrame(selected);c.branchFromFrame(source,selected);}catch(Exception e){throw new RuntimeException(e);}require(new ReplayBoard(p.getSudoku()).equals(source.frames().get(selected).board),"wrong selected board");require(!p.getSudoku().isFixed(0),"answer promoted to given");require(p.getStep()==null,"historical step leaked");});}
   require(Arrays.equals(originalBytes,Files.readAllBytes(data.resolve("source-removed.hrep"))),"archived source bytes changed");
   ReplaySession technique=new ReplaySession(before,2500);technique.elapsedMillis=10;technique.append(Arrays.asList(new ReplayFrame(1,2501,0,"proof","native proof",before,ReplayProof.encode(result.steps.get(0))),new ReplayFrame(1,2510,10,"apply","apply",after,null)));
   edt(()->{try{c.branchFromFrame(technique,1);}catch(Exception e){throw new RuntimeException(e);}require(new ReplayBoard(p.getSudoku()).equals(before)&&p.getStep()==null&&p.currentReasoningChains().isEmpty(),"technique proof branch incorrectly applied or retained unrelated geometry");});
   edt(()->{ReplayBoard preserved=new ReplayBoard(p.getSudoku());String id=c.session().id;try{c.openViewer(source);Path dir=c.directory(),backup=data.resolve("library-backup");Files.move(dir,backup);Files.write(dir,new byte[]{0});try{c.branchFromFrame(source,1);throw new AssertionError("storage failure replaced current attempt");}catch(java.io.IOException expected){require(c.isViewing()&&c.session().id.equals(id)&&new ReplayBoard(p.getSudoku()).equals(preserved),"failure did not preserve current/view");}finally{Files.delete(dir);Files.move(backup,dir);c.retrySaves();c.closeViewer();}}catch(Exception e){throw new RuntimeException(e);}});

   // A wrong player digit with real givens remains a player digit and is still editable.
   Sudoku2 wrong=new Sudoku2();wrong.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");wrong.setCell(2,9,false,true);ReplaySession wrongSource=new ReplaySession(new ReplayBoard(wrong),3000);
   edt(()->{try{c.branchFromFrame(wrongSource,0);}catch(Exception e){throw new RuntimeException(e);}require(p.getSudoku().getValue(2)==9&&!p.getSudoku().isFixed(2)&&p.getSudoku().isFixed(0),"wrong state redefined");p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);p.setActiveCell(2);p.setCellFromCellZoomPanel(4);require(p.getSudoku().getValue(2)==4,"branch not editable");});
   Sudoku2 solved=new Sudoku2();solved.setSudoku("534678912672195348198342567859761423426853791713924856961537284287419635345286179");ReplaySession solvedSource=new ReplaySession(new ReplayBoard(solved),4000);
   edt(()->{try{c.branchFromFrame(solvedSource,0);}catch(Exception e){throw new RuntimeException(e);}require(c.session().completed&&c.session().elapsedMillis==0&&c.session().frames().size()==1,"completed origin fabricated solving time");});
   System.out.println("Native branch confirm/cancel, source-frame exact masks/givens, raw grouped boxes/chains, fresh undo, Enter revalidation, missing-source export/import, source byte preservation and wrong-player edit passed: "+data);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->f.dispose());}System.exit(0);
 }
}
