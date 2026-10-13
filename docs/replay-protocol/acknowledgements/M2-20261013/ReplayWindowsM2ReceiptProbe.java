/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Independently consumes the exact two-frame Windows M2 application return. */
public final class ReplayWindowsM2ReceiptProbe {
    private static final String SHA="5e9bb95a321819db691a69bb9c87465140adea280b0b9d983ab589f9063b2113";
    private static final java.util.List<String> passed=new ArrayList<>();
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);passed.add(message);}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static BufferedImage paint(Component panel,int size){panel.setSize(size,size);BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();return image;}
    private static boolean same(BufferedImage a,BufferedImage b){if(a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight())return false;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))return false;return true;}
    private static int colorCount(BufferedImage image,boolean red){int n=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y),r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;if(red?r>160&&r>g*1.4&&r>b*1.4:b>120&&b>r*1.4&&b>g*1.2)n++;}return n;}
    private static ReplayBoard expectedBoard(){int[] v=new int[81];boolean[] f=new boolean[81];short[] c=new short[81],u=new short[81];v[0]=5;f[0]=true;c[2]=8;u[3]=64;return new ReplayBoard(v,f,c,u);}
    private static void windowImage(MainFrame owner,Path file)throws Exception{owner.validate();BufferedImage image=new BufferedImage(owner.getWidth(),owner.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();owner.paint(g);g.dispose();ImageIO.write(image,"png",file.toFile());}
    public static void main(String[] args)throws Exception{
        Path input=Paths.get(args[0]),out=Files.createDirectories(Paths.get(args[1]));boolean gui=args.length>2&&args[2].equals("--gui");
        System.setProperty("hodoku.data.dir",out.resolve("data").toString());System.setProperty("hodoku.replay.dir",out.resolve("managed").toString());Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        byte[] original=Files.readAllBytes(input);check(original.length==32498&&SHA.equals(ReplayFrameCodec.sha256(original)),"exact Windows application return bytes and SHA-256");
        ReplayFrameCodec.Document doc=ReplayFrameCodec.read(input);check("HRPL-FRAME/0.2".equals(doc.root.get("format")),"M2 profile 0.2");
        ReplaySession session=ReplayFiles.importFile(input);check(session.frameworkSource!=null&&session.frameCount()==2,"actual application import of two common frames");
        check(session.initialAnnotations().length==0,"imported native data stays outside active initial evidence");
        for(int i=0;i<2;i++){
            ReplayFrame f=session.frames().get(i);Map<String,Object> raw=ReplayFrameCodec.map(ReplayFrameCodec.list(doc.root.get("frames")).get(i));
            check(f.board.equals(expectedBoard()),"frame "+i+" exact four board arrays");
            check(f.wallTimeMillis==i+2&&f.elapsedMillis==(i==0?0:27),"frame "+i+" exact wall and elapsed times");
            check(f.label.equals(raw.get("phaseLabel")),"frame "+i+" complete phaseLabel");
            check(f.evidence().length==0&&f.annotations==ReplayAnnotations.EMPTY,"frame "+i+" unknown native stays inert");
            check(f.framework.equals(raw),"frame "+i+" all common and unknown fields preserved");
        }
        check(session.frames().get(0).operationId==session.last().operationId,"operation group retained");
        check(ReplayFramework.notice(session.frames().get(0)).equals("部分观看：缺少重要显示内容。"),"partial frame clearly reports missing important display");
        check(ReplayFramework.notice(session.last()).equals("使用已冻结的完整画面。"),"preview fallback correctly reported");
        Path[] saves={out.resolve("mac-direct-save.hrep"),out.resolve("mac-framework-export.hrep"),out.resolve("mac-store-write.hrep")};
        ReplayFiles.exportFile(saves[0],session,out.resolve("managed"));ReplayFiles.exportFrameworkFile(saves[1],session,out.resolve("managed"));ReplayStore.write(saves[2],session);
        for(Path save:saves){check(Arrays.equals(original,Files.readAllBytes(save)),save.getFileName()+" retains every original byte");ReplaySession reopened=ReplayFiles.importFile(save);check(reopened.frameCount()==2&&reopened.last().board.equals(expectedBoard())&&reopened.last().framework.equals(session.last().framework),save.getFileName()+" actual application reread");}
        Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{try{
            ApplicationAppearance.initialize(AppearanceMode.LIGHT);SudokuPanel panel=new SudokuPanel(null,true);Map<Integer,BufferedImage> light=new HashMap<>();
            for(AppearanceMode mode:new AppearanceMode[]{AppearanceMode.LIGHT,AppearanceMode.DARK}){
                ApplicationAppearance.initialize(mode);
                for(int size:new int[]{480,810}){
                    String tag=mode+"-"+size;ReplayFrame partial=session.frames().get(0);panel.displayReplayFrame(partial);BufferedImage vectors=paint(panel,size);
                    check(colorCount(vectors,true)>20&&colorCount(vectors,false)>20,tag+" partial frame renders red cross and blue outline");
                    check(new ReplayBoard(panel.getSudoku()).equals(expectedBoard()),tag+" vector rendering preserves four arrays");
                    Options.getInstance().setShowCandidates(false);panel.displayReplayFrame(partial);check(same(vectors,paint(panel,size)),tag+" candidate display independent of local toggle");Options.getInstance().setShowCandidates(true);
                    if(mode==AppearanceMode.LIGHT)light.put(size,vectors);else check(same(light.get(size),vectors),tag+" common canvas invariant across themes");
                    ImageIO.write(vectors,"png",out.resolve("partial-"+tag+".png").toFile());
                    ReplayFrame frozen=session.last();panel.displayReplayFrame(frozen);BufferedImage actual=paint(panel,size),png=ReplayFrameRenderer.preview(frozen.framework);
                    check(png.getWidth()==640&&png.getHeight()==640,tag+" source complete PNG dimensions");
                    BufferedImage expected=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=expected.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(png,0,0,size,size,null);g.dispose();
                    check(same(actual,expected),tag+" complete preview pixel exact after proportional scaling");
                    check(new ReplayBoard(panel.getSudoku()).equals(expectedBoard()),tag+" frozen rendering preserves four arrays");
                    ImageIO.write(actual,"png",out.resolve("preview-"+tag+".png").toFile());
                    panel.displayReplayFrame(partial);check(same(vectors,paint(panel,size)),tag+" frame navigation clears prior preview");
                }
            }
            ApplicationAppearance.initialize(AppearanceMode.LIGHT);
        }catch(Throwable t){failure[0]=t;}});if(failure[0]!=null)throw new AssertionError(failure[0]);
        if(gui){SwingUtilities.invokeAndWait(()->{MainFrame owner=null;try{
            owner=new MainFrame(null);owner.setSize(1100,950);owner.setLocation(50,50);owner.setVisible(true);owner.toFront();owner.validate();
            ReplayController controller=owner.getReplayController();ReplayBoard live=new ReplayBoard(owner.getSudokuPanel().getSudoku());controller.openViewer(session);owner.validate();
            check(owner.isShowing()&&controller.viewer().isShowing()&&controller.isViewing(),"actual native MainFrame and replay viewer are showing");
            check(new ReplayBoard(owner.getSudokuPanel().getSudoku()).equals(live),"opening foreign viewer preserves live attempt");
            for(int i=0;i<2;i++){
                if(i==0)controller.viewer().showFrame(0);else ((JButton)field(controller.viewer(),"next")).doClick();
                check(controller.viewer().frameIndex()==i,"actual viewer navigation selects frame "+i);
                check(controller.viewer().hintPanel().getText().equals(session.frames().get(i).label),"actual viewer retains frame "+i+" full explanation");
                check(((JLabel)field(controller.viewer(),"error")).getText().equals(ReplayFramework.notice(session.frames().get(i))),"actual viewer shows frame "+i+" honest completeness notice");
                check(controller.viewer().boardPanel().isShowing()&&new ReplayBoard(controller.viewer().boardPanel().getSudoku()).equals(expectedBoard()),"actual viewer frame "+i+" board is visible and exact");
                windowImage(owner,out.resolve("actual-mac-window-frame-"+i+".png"));
            }
            ((JButton)field(controller.viewer(),"previous")).doClick();check(controller.viewer().frameIndex()==0,"actual previous button returns to first frame");
            String lastId=controller.session().id;
            for(int i=0;i<2;i++){
                controller.branchFromFrame(session,i);ReplaySession branch=controller.session();
                check(!branch.id.equals(session.id)&&!branch.id.equals(lastId),"branch "+i+" independent fresh identity");lastId=branch.id;
                check(new ReplayBoard(owner.getSudokuPanel().getSudoku()).equals(expectedBoard())&&branch.last().board.equals(expectedBoard()),"branch "+i+" actual live and stored four arrays exact");
                check(branch.last().framework==null&&branch.last().evidence().length==0&&branch.initialAnnotations().length==0,"branch "+i+" no executable imported history");
                check(((java.util.List<?>)field(owner.getSudokuPanel(),"doodleStrokes")).isEmpty(),"branch "+i+" historical display absent from active marks");
                check(session.id.equals(branch.sourceReplayId)&&branch.sourceFrameIndex==i,"branch "+i+" provenance identifies selected frame");
                check(Arrays.equals(original,Files.readAllBytes(controller.directory().resolve("sources").resolve(SHA+".hrep"))),"branch "+i+" source archive preserves every byte");
            }
        }catch(Throwable t){failure[0]=t;}finally{if(owner!=null)owner.dispose();}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
        check(Arrays.equals(original,Files.readAllBytes(input)),"source return unchanged after all viewing/save/branch operations");
        StringBuilder report=new StringBuilder("# Windows M2 receipt validation\n\nInput SHA-256: "+SHA+"\n\nMode: "+(gui?"actual macOS native window, programmatic controls and window painting":"headless application renderer")+"\n\n");
        for(String item:passed)report.append("- PASS: ").append(item).append('\n');report.append("\nTotal: ").append(passed.size()).append(" PASS.\n\nNo Windows GUI or physical mouse validation claimed. Native-window PNGs are actual visible Swing window painting, not desktop screen captures.\n");
        Files.write(out.resolve("RESULTS.md"),report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));System.out.println("PASS "+passed.size()+" Windows M2 receipt application/render"+(gui?"/actual native GUI/branch":"")+" checks");System.exit(0);
    }
}
