/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual application import/export/view/branch paths, independently consuming Windows fixtures. */
public final class ReplayFrameworkApplicationProbe {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static BufferedImage paint(SudokuPanel panel,int size)throws Exception{panel.setSize(size,size);BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();return image;}
    static boolean same(BufferedImage a,BufferedImage b){if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))return false;return true;}
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static ReplaySession nativeSession()throws Exception{
        Sudoku2 before=new Sudoku2();before.clearSudoku();Sudoku2 after=before.clone();after.delCandidate(2,1);after.delCandidate(2,2);
        SolutionStep proof=new SolutionStep(SolutionType.NAKED_PAIR);proof.addValue(1);proof.addValue(2);proof.addIndex(0);proof.addIndex(1);proof.addCandidateToDelete(2,1);proof.addCandidateToDelete(2,2);
        ReplaySession session=new ReplaySession(new ReplayBoard(before),1000);session.elapsedMillis=200;
        session.append(Arrays.asList(new ReplayFrame(1,1100,100,"proof","本地双值证明",new ReplayBoard(before),ReplayProof.encode(proof)),new ReplayFrame(1,1200,200,"apply","应用到真实盘面",new ReplayBoard(after),null)));
        return session;
    }
    public static void main(String[] args)throws Exception{
        Path fixtures=Paths.get(args[0]),out=Files.createDirectories(Paths.get(args[1]));boolean gui=args.length>2&&args[2].equals("--gui");
        System.setProperty("hodoku.data.dir",out.resolve("data").toString());System.setProperty("hodoku.replay.dir",out.resolve("managed").toString());Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        SwingUtilities.invokeAndWait(()->ApplicationAppearance.initialize(AppearanceMode.LIGHT));
        ReplaySession windows=ReplayFiles.importFile(fixtures.resolve("all-content.hrep"));
        check(windows.frameworkSource!=null&&windows.frameCount()>1,"W1 did not enter actual application adapter");
        Path saved=out.resolve("mac-w1-unchanged.hrep");ReplayFiles.exportFile(saved,windows,out.resolve("managed"));check(Arrays.equals(Files.readAllBytes(fixtures.resolve("all-content.hrep")),Files.readAllBytes(saved)),"application direct-save changed original bytes");
        ReplaySession noncanonical=ReplayFiles.importFile(fixtures.resolve("noncanonical.hrep"));Path nc=out.resolve("mac-noncanonical.hrep");ReplayFiles.exportFile(nc,noncanonical,out.resolve("managed"));check(Arrays.equals(Files.readAllBytes(nc),Files.readAllBytes(fixtures.resolve("noncanonical.hrep"))),"whitespace/unknown payload not retained");
        try{windows.last().framework.put("phaseLabel","changed");throw new AssertionError("frame map mutable");}catch(UnsupportedOperationException expected){checks++;}
        try{ReplayFiles.exportFrameworkFile(out.resolve("managed/overwrite.hrep"),windows,out.resolve("managed"));throw new AssertionError("managed path overwritten");}catch(IOException expected){checks++;}
        ReplaySession nativeReplay=nativeSession();Path nativeFile=out.resolve("native-proof.hrep");ReplayStore.write(nativeFile,nativeReplay);byte[] nativeBefore=Files.readAllBytes(nativeFile);
        Path nativeExport=out.resolve("mac-native-proof-frames.hrep");ReplayFiles.exportFrameworkFile(nativeExport,nativeReplay,out.resolve("managed"));ReplaySession roundtrip=ReplayFiles.importFile(nativeExport);
        check(roundtrip.frameCount()==nativeReplay.frameCount(),"projection invented/dropped frames");
        for(int i=0;i<roundtrip.frameCount();i++){ReplayFrame a=nativeReplay.frames().get(i),b=roundtrip.frames().get(i);check(a.board.equals(b.board),"actual snapshot changed");check(a.elapsedMillis==b.elapsedMillis&&a.operationId==b.operationId,"time/group changed");check(!ReplayFrameCodec.list(b.framework.get("native")).isEmpty(),"native private evidence lost");}
        check(Arrays.equals(nativeBefore,Files.readAllBytes(nativeFile)),"export changed native replay");
        Path returned=out.resolve("mac-native-return.hrep");ReplayFiles.exportFile(returned,roundtrip,out.resolve("managed"));check(Arrays.equals(Files.readAllBytes(nativeExport),Files.readAllBytes(returned)),"native frame direct save not exact");
        Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{try{
            SudokuPanel board=new SudokuPanel(null,true);
            for(AppearanceMode mode:new AppearanceMode[]{AppearanceMode.LIGHT,AppearanceMode.DARK}){
                ApplicationAppearance.initialize(mode);
                for(int size:new int[]{480,810}){
                    ReplayFrame last=roundtrip.last();board.displayReplayFrame(last);BufferedImage actual=paint(board,size),png=ReplayFrameRenderer.preview(last.framework);
                    BufferedImage expected=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=expected.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(png,0,0,size,size,null);g.dispose();
                    check(same(actual,expected),"frozen native frame differs under size/theme change");check(new ReplayBoard(board.getSudoku()).equals(last.board),"image mutated editable board");ImageIO.write(actual,"png",out.resolve("native-fallback-"+mode+"-"+size+".png").toFile());
                    Options.getInstance().setShowCandidates(false);board.displayReplayFrame(windows.frames().get(1));BufferedImage first=paint(board,size);
                    Options.getInstance().setShowCandidates(true);board.displayReplayFrame(windows.frames().get(1));check(same(first,paint(board,size)),"common candidates depend on local candidate toggle");
                    check(new ReplayBoard(board.getSudoku()).equals(windows.frames().get(1).board),"generic drawing changed board");ImageIO.write(first,"png",out.resolve("windows-generic-"+mode+"-"+size+".png").toFile());
                    board.displayReplayBoard(nativeReplay.last().board,null);paint(board,size);check(field(board,"replayFramework")==null,"frozen view leaked to native next frame");
                }
            }
            ApplicationAppearance.initialize(AppearanceMode.LIGHT);
        }catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);
        ReplaySession edited=ReplayFiles.importFile(nativeExport);edited.elapsedMillis++;
        Path modified=out.resolve("mac-modified.hrep");ReplayFiles.exportFrameworkFile(modified,edited,out.resolve("managed"));ReplayFrameCodec.Document modifiedDoc=ReplayFrameCodec.read(modified);
        check(!ReplayFrameCodec.list(modifiedDoc.root.get("losses")).isEmpty(),"stale private evidence losses missing");check(modifiedDoc.root.get("source")!=null,"edited provenance missing");
        Path archive=out.resolve("replay-sources").resolve(ReplayFrameCodec.sha256(Files.readAllBytes(nativeExport))+".hrep");check(Arrays.equals(Files.readAllBytes(archive),Files.readAllBytes(nativeExport)),"original archive not exact");
        for(Object item:ReplayFrameCodec.list(modifiedDoc.root.get("frames")))check(ReplayFrameCodec.list(ReplayFrameCodec.map(item).get("native")).isEmpty(),"stale native rebound");
        ReplaySession stale=ReplayFiles.importFile(nativeExport);ReplaySession changed=new ReplaySession(stale.id,stale.startedAt);changed.frameworkSource=stale.frameworkSource;changed.elapsedMillis=stale.elapsedMillis;
        for(int i=0;i<stale.frameCount();i++){ReplayFrame f=stale.frames().get(i);Sudoku2 b=f.board.toSudoku();if(i==stale.frameCount()-1)b.delCandidate(8,9);changed.loadFrame(new ReplayFrame(f.operationId,f.wallTimeMillis,f.elapsedMillis,f.kind,f.label,new ReplayBoard(b),null,ReplayAnnotations.EMPTY,f.framework));}
        byte[] preserved=Files.readAllBytes(modified);try{ReplayFiles.exportFrameworkFile(modified,changed,out.resolve("managed"));throw new AssertionError("stale image rebound to changed board");}catch(IOException expected){check(Arrays.equals(preserved,Files.readAllBytes(modified)),"stale-preview rejection replaced existing file");}
        byte[] lastGood=Files.readAllBytes(modified);edited.elapsedMillis=-1;try{ReplayFiles.exportFrameworkFile(modified,edited,out.resolve("managed"));throw new AssertionError("invalid save succeeded");}catch(IOException expected){check(Arrays.equals(lastGood,Files.readAllBytes(modified)),"failed export replaced last good file");}
        // Existing lengthy native content must be shared completely, not truncated to W1's text limit.
        ReplaySession longNative=nativeSession();StringBuilder longLabel=new StringBuilder();for(int i=0;i<20000;i++)longLabel.append("完整说明😀");
        Sudoku2 post=longNative.last().board.toSudoku();post.delCandidate(3,9);longNative.elapsedMillis=300;longNative.append(Collections.singletonList(new ReplayFrame(2,1300,300,"manual",longLabel.toString(),new ReplayBoard(post),null)));
        Path longFile=out.resolve("mac-long-description.hrep");ReplayFiles.exportFrameworkFile(longFile,longNative,out.resolve("managed"));check(ReplayFiles.importFile(longFile).last().label.equals(longLabel.toString()),"long description truncated");
        if(gui){SwingUtilities.invokeAndWait(()->{MainFrame owner=null;try{
            owner=new MainFrame(null);owner.setSize(1000,900);owner.setVisible(true);ReplayController controller=owner.getReplayController();ReplayBoard live=new ReplayBoard(owner.getSudokuPanel().getSudoku());
            controller.openViewer(windows);controller.viewer().showFrame(1);owner.validate();check(controller.isViewing(),"actual viewer failed to open");check(new ReplayBoard(owner.getSudokuPanel().getSudoku()).equals(live),"viewer changed live attempt");
            boolean exportButton=false;for(Component component:controller.viewer().actionBar().getComponents())if(component instanceof JButton&&((JButton)component).getText().contains("跨平台"))exportButton=true;check(exportButton,"cross-platform export not reachable");
            BufferedImage window=new BufferedImage(owner.getWidth(),owner.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=window.createGraphics();owner.paint(g);g.dispose();ImageIO.write(window,"png",out.resolve("actual-windows-frame-viewer.png").toFile());
            controller.closeViewer();controller.openViewer(roundtrip);controller.viewer().showFrame(roundtrip.frameCount()-1);owner.validate();window=new BufferedImage(owner.getWidth(),owner.getHeight(),BufferedImage.TYPE_INT_RGB);g=window.createGraphics();owner.paint(g);g.dispose();ImageIO.write(window,"png",out.resolve("actual-mac-frozen-viewer.png").toFile());
            byte[] original=Files.readAllBytes(nativeExport);ReplayBoard selected=roundtrip.last().board;controller.branchFromFrame(roundtrip,roundtrip.frameCount()-1);
            check(!controller.session().id.equals(roundtrip.id)&&new ReplayBoard(owner.getSudokuPanel().getSudoku()).equals(selected),"real branch identity/board differs");check(((java.util.List<?>)field(owner.getSudokuPanel(),"doodleStrokes")).isEmpty(),"history entered active marks");
            check(Arrays.equals(original,Files.readAllBytes(nativeExport)),"branch changed external source");check(controller.session().last().framework==null,"framework objects became live data");
            Path sourceArchive=controller.directory().resolve("sources").resolve(ReplayFrameCodec.sha256(original)+".hrep");check(Arrays.equals(original,Files.readAllBytes(sourceArchive)),"branch source not archived");
        }catch(Throwable t){failure[0]=t;}finally{if(owner!=null)owner.dispose();}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
        System.out.println("PASS "+checks+" application frame import/export/view/archive/long-text"+(gui?"/actual GUI/branch":"")+" checks");System.exit(0);
    }
}
