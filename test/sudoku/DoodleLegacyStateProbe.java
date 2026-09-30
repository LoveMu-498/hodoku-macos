package sudoku;
import java.beans.*;import java.io.*;import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** An actual legacy-shaped XML state without any hypothesis properties stays ordinary ink. */
public final class DoodleLegacyStateProbe {
 public static void main(String[]args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  initialize();mark(0,1,1,0);DoodleStroke old=find(0,1,1,0).copy();old.setHypothesisStart(false);old.setHypothesisConditionId(0);old.setHypothesisGroupKind(0);old.setConclusionSourceMask(0);
  GuiState state=new GuiState(p,p.getSolver(),f.getSolutionPanel());state.setIncludeAnnotations(true);state.get(true);state.setDoodleStrokes(new ArrayList<DoodleStroke>(Arrays.asList(old)));state.setDoodleUndoHistory(new ArrayList<List<DoodleStroke>>());state.setDoodleRedoHistory(new ArrayList<List<DoodleStroke>>());state.setDoodleHypothesisConsumedMask(0);state.setDoodleHypothesisUndoMasks(null);state.setDoodleHypothesisRedoMasks(null);
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(XMLEncoder encoder=new XMLEncoder(out)){encoder.writeObject(state);}String xml=new String(out.toByteArray(),"UTF-8");check(!xml.contains("Hypothesis")&&!xml.contains("hypothesis")&&!xml.contains("conclusionSourceMask"),"fixture unexpectedly encoded new metadata");
  GuiState loaded;try(XMLDecoder decoder=new XMLDecoder(new ByteArrayInputStream(out.toByteArray()))){loaded=(GuiState)decoder.readObject();}loaded.initialize(p,p.getSolver(),f.getSolutionPanel());loaded.set();
  check(find(0,1,1,0)!=null&&!find(0,1,1,0).isHypothesisStart(),"legacy mark lost or auto-inferred start");mark(10,2,1,0);check(!find(10,2,1,0).isHypothesisStart(),"legacy nonempty branch restarted automatic premise");
  p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);p.toggleDoodleHypothesisEntry();mark(20,3,1,0);check(find(20,3,1,0).isHypothesisStart(),"legacy state could not explicitly add premise");
  System.out.println("PASS: legacy-shaped XML without new properties restores original ink, infers no starts, supports explicit new start");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
