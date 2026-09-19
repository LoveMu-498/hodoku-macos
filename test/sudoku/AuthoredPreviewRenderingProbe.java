package sudoku;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import static sudoku.GroupedChainTransactionProbe.*;

/** Focus the complete authored chain, dim old context, and retain later research annotations. */
public final class AuthoredPreviewRenderingProbe {
    static BufferedImage render() {
        p.setSize(810,810);
        BufferedImage image=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();p.paint(g);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception {
        try {
            ApplicationAppearance.initialize(args.length>0?AppearanceMode.DARK:AppearanceMode.LIGHT);
            for(boolean placement:new boolean[]{false,true}) {
                edt(()->{
                    if(f==null){f=new MainFrame(null);p=f.getSudokuPanel();}
                    p.setSudoku((String)null);p.getSudoku().set(ChainAdvisoryProbe.board(true));
                    p.setShowCandidates(true);p.setSize(810,810);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
                    UserChain c=placement?ChainAdvisoryProbe.ring():GroupedChainProbe.chain(false,
                            new UserChainNode[]{GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,1)},true);
                    c.setActive(true);Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);a.set(p,c);
                    call("noteUserChainReasoningChanged");
                    AnnotationTimelineProbe.ink().add(AnnotationTimelineProbe.line(500,Color.MAGENTA));
                    render();call("handleReasoningEnter");return null;
                });await(true);
                edt(()->{
                    SolutionStep result=p.getStep();
                    BufferedImage preview=render();
                    List<Point2D> targets=new ArrayList<Point2D>();
                    Method center=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);center.setAccessible(true);
                    int size=(Integer)read("cellSize");
                    for(Candidate c:result.getCandidatesToDelete())targets.add((Point2D)center.invoke(p,c.getIndex(),c.getValue(),size));
                    if(result.isAuthoredPlacement())for(int i=0;i<result.getIndices().size();i++)
                        targets.add((Point2D)center.invoke(p,result.getIndices().get(i),result.getValues().get(i),size));
                    check(!targets.isEmpty(),"no conclusions tested");
                    AnnotationTimelineProbe.ink().add(AnnotationTimelineProbe.line(550,Color.BLUE));
                    BufferedImage later=render();
                    check(later.getRGB(450,550)==Color.BLUE.getRGB(),"new research ink faded");
                    p.setShowHintCellValue(9);BufferedImage highlighted=render();
                    check(p.getStep()==result,"candidate highlighting canceled preview");
                    Point2D highlightTarget=(Point2D)center.invoke(p,80,9,size);
                    check(difference(later,highlighted,highlightTarget,6)>0,"candidate highlight did not display during preview");
                    p.setShowHintCellValue(9);
                    p.cancelReasoningFromUi();BufferedImage original=render();
                    Point2D source=(Point2D)center.invoke(p,1,1,size);
                    check(difference(preview,original,source,6)==0,"original chain node changed");
                    Point2D unrelated=(Point2D)center.invoke(p,80,9,size);
                    check(difference(preview,original,unrelated,6)==0,"ordinary candidates changed during preview");
                    check(preview.getRGB(450,500)!=Color.MAGENTA.getRGB(),"old unrelated ink not faded");
                    check(original.getRGB(450,500)==Color.MAGENTA.getRGB(),"cancel did not restore old ink");
                    int changed=0;for(Point2D target:targets)changed+=difference(preview,original,target,8);
                    check(changed>0,"conclusion not highlighted");
                    if(!placement)check(vividRedPixels(preview)>100,"deletion is not vivid red");
                    java.io.File out=new java.io.File(System.getProperty("java.io.tmpdir"),"authored-"+(placement?"placement":"deletion")+".png");
                    javax.imageio.ImageIO.write(preview,"png",out);
                    System.out.println(out+": full chain preserved, old context faded, new ink clear, cancellation restored");return null;
                });
            }
            edt(()->{
                p.setSudoku((String)null);p.getSudoku().set(ChainAdvisoryProbe.board(false));
                p.setAnnotationTool(AnnotationTool.FREE_CHAIN);Options.getInstance().setMarkInvalidLinks(true);
                ChainEditingProbe.install(ChainAdvisoryProbe.ring());call("handleReasoningEnter");return null;
            });await(true);
            edt(()->{
                BufferedImage conditional=render();
                check(redPixels(conditional,0,0,240,220)>20,"preview lost red unverified-link warning");
                p.cancelReasoningFromUi();check(redPixels(render(),0,0,240,220)>20,"editing diagnostics not restored");
                System.out.println("Conditional preview and editing both retain red relation diagnostics");return null;
            });
            boxPreview();
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
        finally {if(f!=null)edt(()->{f.dispose();return null;});}
        System.exit(0);
    }
    static int difference(BufferedImage a,BufferedImage b,Point2D center,int radius) {
        int count=0;for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++) {
            int x=(int)Math.round(center.getX())+dx,y=(int)Math.round(center.getY())+dy;
            if(a.getRGB(x,y)!=b.getRGB(x,y))count++;
        }return count;
    }
    static int redPixels(BufferedImage image,int x0,int y0,int x1,int y1) {
        int count=0;for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
            Color c=new Color(image.getRGB(x,y));if(c.getRed()>180&&c.getRed()>c.getGreen()*1.5&&c.getRed()>c.getBlue()*1.5)count++;
        }return count;
    }
    static int vividRedPixels(BufferedImage image) {
        int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            Color c=new Color(image.getRGB(x,y));if(c.getRed()>220&&c.getGreen()<30&&c.getBlue()<30)count++;
        }return count;
    }
    static void boxPreview()throws Exception {
        edt(()->{
            String[] candidates=SwordfishBoxReasoningProbe.CANDIDATES.split(" ");StringBuilder values=new StringBuilder();
            for(String c:candidates)values.append(c.length()==1?c:"0");p.setSudoku(values.toString());
            for(int c=0;c<81;c++)if(candidates[c].length()>1)for(int d=1;d<=9;d++)
                if(!candidates[c].contains(""+d))p.getSudoku().delCandidate(c,d);
            p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.BOX_SELECTION);
            SwordfishBoxReasoningProbe.boxes();call("handleReasoningEnter");return null;
        });await(true);
        edt(()->{
            check(p.getStep().getType()==SolutionType.SWORDFISH,"box preview lost original fish");
            BufferedImage preview=render();p.cancelReasoningFromUi();BufferedImage original=render();
            Method center=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);center.setAccessible(true);
            int size=(Integer)read("cellSize");
            check(difference(preview,original,(Point2D)center.invoke(p,0,5,size),15)==0,"given number faded");
            check(difference(preview,original,(Point2D)center.invoke(p,25,6,size),6)==0,"box preview changed ordinary candidate");
            check(vividRedPixels(preview)>100,"box deletion not vivid red");
            javax.imageio.ImageIO.write(preview,"png",new java.io.File(System.getProperty("java.io.tmpdir"),"box-focus.png"));
            System.out.println("Swordfish box preserves givens, ordinary candidates and proof, highlights six deletions");return null;
        });
    }
}
