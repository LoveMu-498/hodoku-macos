package sudoku;
import java.lang.reflect.*;
import java.util.*;
import static sudoku.GroupedChainProbe.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** Conditional versus certified results, and the actual preview/apply/undo controller. */
public final class ChainAdvisoryProbe {
 static UserChain ring(){return chain(true,new UserChainNode[]{node(1,0),node(1,1),node(1,9)},true,false,true);}
 static Sudoku2 board(boolean certified){Sudoku2 b=blank();if(certified){for(int c=2;c<9;c++)b.delCandidate(c,1);for(int r=2;r<9;r++)b.delCandidate(r*9,1);}return b;}
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void waitPreview()throws Exception {try{await(true);}catch(AssertionError e){edt(()->{System.out.println("request="+read("reasoningRequest")+" proposal="+read("reasoningProposal")+" step="+p.getStep()+" chains="+p.currentReasoningChains().size());for(UserChain c:p.currentReasoningChains())System.out.println("chain closed="+c.isClosed()+" edges="+c.getStrongRelations()+" result="+UserChainValidator.preview(p.getSudoku(),c).status+" steps="+UserChainValidator.preview(p.getSudoku(),c).steps);return null;});throw e;}}
 public static void main(String[] args)throws Exception{try{
  for(boolean certified:new boolean[]{true,false}){ System.out.println("fixture="+certified);
   Sudoku2 board=board(certified);UserChainValidator.Result strict=UserChainValidator.validate(board,ring()),preview=UserChainValidator.preview(board,ring());
   require(strict.status==(certified?UserChainValidator.Status.PROVEN:UserChainValidator.Status.INVALID),"strict diagnosis");
   require(preview.status==(certified?UserChainValidator.Status.PROVEN:UserChainValidator.Status.ASSUMED),"preview status");
   SolutionStep first=preview.steps.get(0);require(first.isAuthoredPlacement()&&first.getIndices().get(0)==0&&first.getValues().get(0)==1,"true node must fill");
   require(first.getType()==SolutionType.DISCONTINUOUS_NICE_LOOP&&first.getAnzSet()==1,"native type/count");require(first.toString().contains("=1"),"fill text absent");
   require(((SolutionStep)first.clone()).isAuthoredPlacement(),"clone loses fill");
   edt(()->{if(f==null){f=new MainFrame(null);p=f.getSudokuPanel();}p.setSudoku((String)null);p.getSudoku().set(board);p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);UserChain c=ring();c.setActive(true);Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);a.set(p,c);call("noteUserChainReasoningChanged");p.selectReasoningHint(new SolutionStep(SolutionType.HIDDEN_RECTANGLE),null,null,false);call("handleReasoningEnter");return null;});waitPreview();
   edt(()->{require(p.getSudoku().getValue(0)==0,"first Enter mutated board");require(((Set<?>)read("invalidUserChainRelations")).size()==(certified?0:2),"diagnostic edges lost");p.setSize(720,720);java.awt.image.BufferedImage im=new java.awt.image.BufferedImage(720,720,1);p.paint(im.getGraphics());javax.imageio.ImageIO.write(im,"png",new java.io.File("/tmp/hodoku-advisory-build/preview-"+certified+".png"));p.cancelReasoningFromUi();require(p.getSudoku().getValue(0)==0,"Esc mutated board");call("handleReasoningEnter");return null;});waitPreview();
   edt(()->{call("handleReasoningEnter");return null;});await(false);
   edt(()->{require(p.getSudoku().getValue(0)==1,"second Enter did not fill");p.undo();require(p.getSudoku().getValue(0)==0,"undo did not restore");return null;});
  }
  UserChain x=chain(false,new UserChainNode[]{node(1,0),node(1,1)},true);
  require(UserChainValidator.preview(blank(),x).steps.get(0).getType()==SolutionType.X_CHAIN,"X chain naming");
  Sudoku2 xyBoard=blank();for(int cell:new int[]{0,1})for(int d=3;d<=9;d++)xyBoard.delCandidate(cell,d);
  UserChain xy=chain(false,new UserChainNode[]{node(1,0),node(2,0),node(2,1),node(1,1)},true,false,true);
  require(UserChainValidator.preview(xyBoard,xy).steps.get(0).getType()==SolutionType.XY_CHAIN,"XY chain naming");
  require(UserChainValidator.preview(blank(),xy).steps.get(0).getType()==SolutionType.AIC,"AIC naming");
  UserChain fallback=chain(false,new UserChainNode[]{node(1,0),node(1,1),node(1,9)},true,true);
  require(UserChainValidator.preview(blank(),fallback).steps.get(0).getType()==SolutionType.FORCING_CHAIN_CONTRADICTION,"forcing fallback");
  System.out.println("Certified and assumed preview, red diagnostics, Esc, direct fill, undo and X/XY/AIC/fallback passed");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
