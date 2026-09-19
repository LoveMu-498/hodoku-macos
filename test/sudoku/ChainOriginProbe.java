package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Actual mouse/modifier handlers and pixels, using isolated settings. */
public final class ChainOriginProbe {
    static Point candidate(int cell,int digit)throws Exception {
        Method m=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);m.setAccessible(true);
        Point2D at=(Point2D)m.invoke(p,cell,digit,(Integer)read("cellSize"));return new Point((int)at.getX(),(int)at.getY());
    }
    static BufferedImage render(String name)throws Exception {
        p.setSize(810,810);BufferedImage image=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();p.paint(g);g.dispose();
        if(name!=null)ImageIO.write(image,"png",new java.io.File(System.getProperty("java.io.tmpdir"),name+".png"));
        return image;
    }
    static void alt(boolean down) {
        p.updateDeletionModifier(new KeyEvent(p,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),down?InputEvent.ALT_DOWN_MASK:0,KeyEvent.VK_ALT,KeyEvent.CHAR_UNDEFINED));
    }
    static int bluePixels(BufferedImage image,Point center) {
        int count=0;for(int y=center.y-23;y<=center.y+23;y++)for(int x=center.x-23;x<=center.x+23;x++) {
            if(x<0||y<0||x>=image.getWidth()||y>=image.getHeight())continue;
            Color c=new Color(image.getRGB(x,y));if(c.getBlue()>180&&c.getBlue()>c.getRed()+50&&c.getBlue()>c.getGreen()+30)count++;
        }return count;
    }
    public static void main(String[] args)throws Exception {
        try {
            edt(()->{
                ApplicationAppearance.initialize(args.length>0?AppearanceMode.DARK:AppearanceMode.LIGHT);
                f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());
                p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);render(null);
                check(p.currentChainOrigin()==null,"empty board has an origin");
                click(1,0,candidate(0,1));check(p.currentChainOrigin().contains(0,1),"first mark not next origin");
                BufferedImage marked=render("origin-single");Point first=candidate(0,1);
                alt(true);check(p.currentChainOrigin()==null,"Option kept origin");
                BufferedImage option=render("origin-option");check(bluePixels(marked,first)>bluePixels(option,first)+20,"origin outline missing");
                alt(false);check(p.currentChainOrigin().contains(0,1),"Option release lost origin");
                click(1,InputEvent.SHIFT_DOWN_MASK,candidate(1,1));check(p.currentChainOrigin().cells().length==2,"group origin missing");
                render("origin-group");
                click(1,0,candidate(12,1));click(3,InputEvent.SHIFT_DOWN_MASK,candidate(12,1));
                check(p.currentChainOrigin()==null,"finished segment still claims next departure");
                click(1,0,candidate(30,1));BufferedImage beforeHover=render(null);
                event(MouseEvent.MOUSE_MOVED,MouseEvent.NOBUTTON,0,candidate(1,1));
                BufferedImage hovered=render("origin-group-hover");
                check(bluePixels(hovered,candidate(1,1))>bluePixels(beforeHover,candidate(1,1))+10,"whole-group target preview missing");
                click(1,0,candidate(1,1));check(p.currentChainOrigin().cells().length==2,"existing group became single");
                check(UserChainAssembly.assemble(p.currentReasoningChains()).chain!=null,"shared group did not connect segments");
                click(1,InputEvent.ALT_DOWN_MASK,candidate(60,1));check(p.currentChainOrigin()==null,"held Option showed departure");
                alt(false);check(p.currentChainOrigin().contains(60,1),"new origin not restored on release");
                UserChainAssembly.Result split=UserChainAssembly.assemble(p.currentReasoningChains());
                check(split.problem==UserChainValidator.Problem.DISCONNECTED_INPUT&&split.componentAnchors.size()==2,"independent component count");
                call("handleReasoningEnter");check(read("reasoningProposal")==null,"disconnected chains analyzed partially");
                Field status=MainFrame.class.getDeclaredField("statusLabelCellCandidate");status.setAccessible(true);
                String message=((javax.swing.JLabel)status.get(f)).getText();check(message.contains("2")&&message.contains("r7c7"),"disconnected hint missing count/location");
                alt(true);p.cancelAnnotationToolInteractionOnDeactivation();check(p.currentChainOrigin()!=null,"focus loss left sticky Option");
                // Complete group expansion must update exact references in prior segments, with one undo.
                p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setAnnotationTool(AnnotationTool.FREE_CHAIN);render(null);
                click(1,0,candidate(12,1));click(1,0,candidate(0,1));click(3,InputEvent.SHIFT_DOWN_MASK,candidate(0,1));
                click(1,0,candidate(0,1));click(1,InputEvent.SHIFT_DOWN_MASK,candidate(1,1));
                check(done().get(0).getNodes().get(1).grouped(),"prior connection left at single member");
                p.undoCurrentAnnotation();check(!done().get(0).getNodes().get(1).grouped(),"group-reference expansion not atomic undo");
                // Distinct overlapping groups must not be silently merged or chosen.
                done().add(GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,20)},true));
                done().add(GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,9),GroupedChainProbe.node(1,30)},true));
                UserChain before=active();int count=before.getNodes().size();click(1,0,candidate(0,1));
                check(active()==before&&active().getNodes().size()==count,"ambiguous group changed input");
                longGroupedChain();
                call("handleReasoningEnter");check(p.currentChainOrigin()==null,"analysis retained a drawing departure");
                System.out.println("Origin/Option pixels, full-group reconnect, atomic group expansion, ambiguity and all-components diagnostics passed");return null;
            });
            await(true);edt(()->{check(p.currentChainOrigin()==null,"preview retained a drawing departure");p.cancelReasoningFromUi();return null;});
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        finally {if(f!=null)edt(()->{f.dispose();return null;});}
        System.exit(0);
    }
    static void longGroupedChain()throws Exception {
        p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
        int[][] rows={{2,4,5,8},{2,3,4,5,7,8},{2,5,7,8},{7,8},{},{},{2,3,4,5},{},{2,5}};
        for(int r=0;r<9;r++)for(int c=1;c<=9;c++) {
            boolean keep=false;for(int x:rows[r])if(x==c)keep=true;if(!keep)p.getSudoku().delCandidate(r*9+c-1,3);
        }
        set("nextUserChainStrong",true);render(null);
        click(1,0,candidate(11,3));click(1,0,candidate(1,3));
        click(1,InputEvent.SHIFT_DOWN_MASK,candidate(10,3));click(1,InputEvent.SHIFT_DOWN_MASK,candidate(19,3));
        click(3,InputEvent.SHIFT_DOWN_MASK,candidate(19,3));
        click(1,0,candidate(19,3));check(p.currentChainOrigin().cells().length==3,"three-member continuation became singleton");
        p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,1,0,KeyEvent.VK_SPACE,' '));
        click(1,0,candidate(73,3));click(1,0,candidate(55,3));click(1,InputEvent.SHIFT_DOWN_MASK,candidate(56,3));
        click(1,0,candidate(57,3));click(1,InputEvent.SHIFT_DOWN_MASK,candidate(58,3));click(1,0,candidate(76,3));
        assertDeletesTarget(false);render("origin-long-grouped");
        click(1,0,candidate(13,3));
        p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,1,0,KeyEvent.VK_SPACE,' ')); // Two weak links.
        click(1,0,candidate(11,3));assertDeletesTarget(true);
        System.out.println("Screenshot candidate-3 fixture: real split-group mouse input proves r2c5<>3 as open chain and double-weak loop");
    }
    static void assertDeletesTarget(boolean closed) {
        UserChainAssembly.Result assembled=UserChainAssembly.assemble(p.currentReasoningChains());
        check(assembled.chain!=null&&assembled.chain.isClosed()==closed,"long chain assembly failed: "+assembled.problem);
        UserChainValidator.Result proof=UserChainValidator.validate(p.getSudoku(),assembled.chain);boolean deletes=false;
        for(SolutionStep step:proof.steps)for(Candidate target:step.getCandidatesToDelete())
            if(target.getIndex()==13&&target.getValue()==3)deletes=true;
        check(proof.status==UserChainValidator.Status.PROVEN&&deletes,"long proof missing r2c5<>3");
    }
}
