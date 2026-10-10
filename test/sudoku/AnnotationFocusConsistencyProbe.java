/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Actual panel paint and image export on isolated synthetic data; never displays a window. */
public final class AnnotationFocusConsistencyProbe {
    private static final int CELL = 39, DIGIT = 5, DENSITY = 2;
    private static final AnnotationTool[] TOOLS = { AnnotationTool.DEFAULT_MOUSE,
        AnnotationTool.CELL_COLORING, AnnotationTool.DOODLE, AnnotationTool.FREE_CHAIN,
        AnnotationTool.BOX_SELECTION };
    private static final List<String> failures = new ArrayList<String>();
    private static final Properties metrics = new Properties(), referenceMetrics = new Properties();
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static Path output, baseline;
    private static boolean recording;
    private static String context;
    private static int checks;

    private AnnotationFocusConsistencyProbe() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        recording = args.length == 1 && "--record-baseline".equals(args[0]);
        output = Paths.get(System.getProperty("hodoku.probe.output", "/tmp/hodoku-annotation-focus"));
        String before = System.getProperty("hodoku.probe.baseline");
        baseline = before == null ? null : Paths.get(before);
        Files.createDirectories(output);
        if (!recording) {
            if (baseline == null) throw new IllegalArgumentException("hodoku.probe.baseline is required");
            try (InputStream stream = Files.newInputStream(baseline.resolve("metrics.properties"))) {
                referenceMetrics.load(stream);
            }
        }
        FutureTask<Void> task = new FutureTask<Void>(() -> {
            try {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);
                frame = new MainFrame(null);
                panel = frame.getSudokuPanel();
                for (AppearanceMode theme : new AppearanceMode[]{AppearanceMode.LIGHT, AppearanceMode.DARK}) {
                    ApplicationAppearance.initialize(theme);
                    for (int size : new int[]{630, 1134}) {
                        context = theme.name().toLowerCase() + "-" + size;
                        run("chain-font", () -> chainFont(size));
                        run("authored-font", () -> authoredFont(size));
                        run("native-unchanged", () -> nativeUnchanged(size));
                        run("focus-consistency", () -> focusConsistency(size));
                        run("shared-sources", () -> sharedSources(size));
                        run("unrelated-unchanged", () -> unrelatedUnchanged(size));
                    }
                }
            } finally { if (frame != null) frame.dispose(); }
            return null;
        });
        SwingUtilities.invokeAndWait(task);
        try { task.get(); }
        catch (Throwable error) { error.printStackTrace(); System.exit(2); }
        try (OutputStream stream = Files.newOutputStream(output.resolve("metrics.properties"))) {
            metrics.store(stream, "Observed source geometry and actual white-glyph mask metrics");
        }
        StringBuilder report = new StringBuilder("Actual SudokuPanel.paint / getSudokuImage; hidden Swing frame.\n")
                .append(recording ? "Before-product capture; new-contract assertions may fail.\n" : "Product compared with before-product capture.\n")
                .append(checks).append(" assertions; ").append(failures.size()).append(" failures.\n");
        for (String failure : failures) report.append("FAIL ").append(failure).append('\n');
        Files.write(output.resolve("results.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(report);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private interface Scenario { void run() throws Exception; }
    private static void run(String name, Scenario action) {
        try { action.run(); }
        catch (Throwable error) { failures.add(context + " " + name + " fixture: " + error); error.printStackTrace(); }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(context + " " + message);
    }
    private static Object read(String name) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(panel);
    }
    private static void write(String name, Object value) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true); field.set(panel, value);
    }
    private static Object invoke(String name, Class<?>[] types, Object... values) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(panel, values);
    }
    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>)read("doodleStrokes"); }
    @SuppressWarnings("unchecked")
    private static List<UserChain> chains() throws Exception { return (List<UserChain>)read("userChains"); }
    @SuppressWarnings("unchecked")
    private static List<SudokuSet> boxes() throws Exception { return (List<SudokuSet>)read("boxReasoningGroups"); }
    @SuppressWarnings("unchecked")
    private static Map<Integer,Color> colors(String name) throws Exception { return (Map<Integer,Color>)read(name); }
    @SuppressWarnings("unchecked")
    private static void reset(int size) throws Exception {
        panel.setSudoku((String)null);
        panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0'));
        panel.setShowCandidates(true); panel.setReplayReadOnly(false);
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
        panel.getCellZoomPanel().selectPaletteGroup(0);
        panel.setSize(size,size); panel.setDoodleWidthIndex(1);
        ink().clear(); chains().clear(); write("activeUserChain",null); write("reasoningProposal",null);
        colors("coloringMap").clear(); colors("coloringCandidateMap").clear();
        for (SudokuSet group : boxes()) group.clear();
        ((List<Integer>)read("cellSelection")).clear();
        Arrays.fill((boolean[])read("showHintCellValues"),false);
        paint();
    }
    private static BufferedImage paint() {
        BufferedImage image = new BufferedImage(panel.getWidth()*DENSITY,panel.getHeight()*DENSITY,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { g.scale(DENSITY,DENSITY); panel.paint(g); }
        finally { g.dispose(); }
        return image;
    }
    private static Color color(int group) { return Options.getInstance().getColoringColors()[group*2]; }
    private static UserChain node(int digit, int group) throws Exception {
        UserChain chain = new UserChain();
        chain.getNodes().add(new UserChainNode(CELL,digit,color(group)));
        chains().add(chain); return chain;
    }
    private static UserChain separateEndpointCue() throws Exception {
        // L's endpoint blue ring is an independent interaction cue, not source focus.
        // A disconnected distant node gives this fixture no single current endpoint.
        UserChain distant = new UserChain();
        distant.getNodes().add(new UserChainNode(80,9,color(0)));
        chains().add(distant); return distant;
    }
    private static DoodleStroke mark(int digit, int kind, int group) throws Exception {
        DoodleStroke stroke = (DoodleStroke)invoke("createCandidateHypothesisMark",
                new Class<?>[]{int.class,int.class,int.class,int.class,Color.class},CELL,digit,kind,group,color(group));
        ink().add(stroke); return stroke;
    }
    private static Point2D center(int digit) throws Exception {
        return (Point2D)invoke("doodleCandidateGlyphCenter",new Class<?>[]{int.class,int.class},CELL,digit);
    }
    private static Rectangle cellArea(int density) throws Exception {
        int size = (Integer)read("cellSize");
        return pixels(panel.getX(CELL/9,CELL%9),panel.getY(CELL/9,CELL%9),size,size,density);
    }
    private static Rectangle candidateArea(int digit, int density) throws Exception {
        Point2D point = center(digit); double half = (Integer)read("cellSize")/6.0;
        return pixels(point.getX()-half,point.getY()-half,half*2,half*2,density);
    }
    private static Rectangle pixels(double x,double y,double width,double height,int density) {
        int left=(int)Math.floor(x*density),top=(int)Math.floor(y*density);
        return new Rectangle(left,top,(int)Math.ceil((x+width)*density)-left,(int)Math.ceil((y+height)*density)-top);
    }
    private static int distance(int a,int b) {
        return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));
    }
    private static int difference(BufferedImage a,BufferedImage b,Rectangle area,int tolerance) {
        int result=0;
        for(int y=area.y;y<area.y+area.height;y++) for(int x=area.x;x<area.x+area.width;x++)
            if(distance(a.getRGB(x,y),b.getRGB(x,y))>tolerance)result++;
        return result;
    }
    private static void save(String name,BufferedImage image,int density) throws Exception {
        ImageIO.write(image,"png",output.resolve(context+"-"+name+".png").toFile());
        Rectangle crop=cellArea(density);
        ImageIO.write(image.getSubimage(crop.x,crop.y,crop.width,crop.height),"png",
                output.resolve(context+"-"+name+"-cell.png").toFile());
    }
    private static BufferedImage before(String name) throws Exception {
        Path path=baseline.resolve(context+"-"+name+".png");
        check(Files.isRegularFile(path),"missing before image "+path.getFileName());
        return Files.isRegularFile(path)?ImageIO.read(path.toFile()):null;
    }
    private static void unchanged(String name,BufferedImage image,int density) throws Exception {
        save(name,image,density);
        if(recording)return;
        BufferedImage original=before(name);
        if(original!=null)check(difference(original,image,new Rectangle(0,0,image.getWidth(),image.getHeight()),0)==0,
                name+" pixels changed from before product");
    }
    private static void unchangedOutsideGlyph(String name,BufferedImage image,int digit,int density) throws Exception {
        save(name,image,density);
        Point2D anchor=center(digit);
        String key=context+"-"+name;
        String geometry=read("cellSize")+"|"+anchor.getX()+"|"+anchor.getY();
        metrics.setProperty(key+".geometry",geometry);
        if(recording)return;
        check(geometry.equals(referenceMetrics.getProperty(key+".geometry")),name+" common candidate anchor moved");
        BufferedImage original=before(name);
        if(original==null)return;
        // Only the old full-size bold glyph's footprint may change; this includes the
        // new smaller base-style glyph, but excludes the circle's external contour.
        Rectangle allowed=glyphRegion(digit,density,1.0f,Font.BOLD);
        allowed.grow(density,density);
        int changed=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if(!allowed.contains(x,y)&&distance(original.getRGB(x,y),image.getRGB(x,y))>0)changed++;
        check(changed==0,name+" changed circle geometry or unrelated pixels: "+changed);
    }
    private static Rectangle glyphRegion(int digit,int density,float scale,int style) throws Exception {
        Font base=(Font)read("candidateFont");
        Font font=base.deriveFont(style,base.getSize2D()*scale);
        BufferedImage scratch=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=scratch.createGraphics();
        try {
            g.scale(density,density); hints(g);
            Rectangle2D bounds=font.createGlyphVector(g.getFontRenderContext(),Integer.toString(digit)).getVisualBounds();
            Point2D point=center(digit);
            return pixels(point.getX()-bounds.getWidth()/2,point.getY()-bounds.getHeight()/2,
                    bounds.getWidth(),bounds.getHeight(),density);
        } finally {g.dispose();}
    }
    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    private static BufferedImage glyphReference(BufferedImage canvas,int digit,int density,float scale) throws Exception {
        BufferedImage result=new BufferedImage(canvas.getWidth(),canvas.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=result.createGraphics();
        try {
            g.scale(density,density);hints(g);
            Font base=(Font)read("candidateFont"),font=base.deriveFont(base.getSize2D()*scale);
            g.setFont(font);g.setColor(Color.WHITE);
            Rectangle2D bounds=font.createGlyphVector(g.getFontRenderContext(),Integer.toString(digit)).getVisualBounds();
            Point2D point=center(digit);
            g.drawString(Integer.toString(digit),(float)(point.getX()-bounds.getCenterX()),(float)(point.getY()-bounds.getCenterY()));
        } finally {g.dispose();}
        return result;
    }
    private static void assertSmallBaseGlyph(String label,BufferedImage image,int digit,int density) throws Exception {
        BufferedImage expected=glyphReference(image,digit,density,.90f);
        Rectangle region=glyphRegion(digit,density,1.0f,Font.BOLD);region.grow(density,density);
        Map<Integer,Integer> backgroundCounts=new java.util.HashMap<Integer,Integer>();
        for(int y=region.y;y<region.y+region.height;y++)for(int x=region.x;x<region.x+region.width;x++) {
            int rgb=image.getRGB(x,y);
            if(distance(rgb,Color.WHITE.getRGB())>100)
                backgroundCounts.put(rgb,backgroundCounts.containsKey(rgb)?backgroundCounts.get(rgb)+1:1);
        }
        int background=0,largest=0;
        for(Map.Entry<Integer,Integer> entry:backgroundCounts.entrySet()) if(entry.getValue()>largest) {
            largest=entry.getValue();background=entry.getKey();
        }
        int expectedCore=0,actualCore=0,shared=0,union=0;
        for(int y=region.y;y<region.y+region.height;y++)for(int x=region.x;x<region.x+region.width;x++) {
            // The real glyph is white over a colored solid fill. Compare the reference
            // after that same compositing, rather than equating alpha with RGB whiteness.
            int alpha=expected.getRGB(x,y)>>>24;
            int red=((background>>16&255)*(255-alpha)+255*alpha+127)/255;
            int green=((background>>8&255)*(255-alpha)+255*alpha+127)/255;
            int blue=((background&255)*(255-alpha)+255*alpha+127)/255;
            boolean wanted=distance(red<<16|green<<8|blue,Color.WHITE.getRGB())<=55;
            boolean actual=distance(image.getRGB(x,y),Color.WHITE.getRGB())<=55;
            if(wanted)expectedCore++;if(actual)actualCore++;if(wanted&&actual)shared++;if(wanted||actual)union++;
        }
        metrics.setProperty(context+"-"+label+".white-mask",actualCore+"|"+expectedCore+"|"+shared+"|"+union);
        check(expectedCore>3,label+" white glyph reference is too small");
        check(union>0 && shared>=union*.82,label+" white glyph is not centered 90% base font: actual="+actualCore
                +" expected="+expectedCore+" intersection="+shared+" union="+union);
        ImageIO.write(expected,"png",output.resolve(context+"-"+label+"-expected-glyph.png").toFile());
    }

    private static void chainFont(int size) throws Exception {
        reset(size);unchanged("bare-board",paint(),DENSITY);
        for(int digit:new int[]{1,5,8}) {
            reset(size);node(digit,3);
            BufferedImage mouse=paint();
            String label="L-digit-"+digit;
            assertSmallBaseGlyph(label,mouse,digit,DENSITY);
            unchangedOutsideGlyph(label,mouse,digit,DENSITY);
            UserChain distant=separateEndpointCue();
            for(AnnotationTool tool:TOOLS) {
                panel.setAnnotationTool(tool);panel.getCellZoomPanel().selectPaletteGroup(5);
                BufferedImage other=paint();
                check(difference(mouse,other,cellArea(DENSITY),0)==0,label+" differs from M in "+tool);
            }
            chains().remove(distant);
            panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
            BufferedImage exported=panel.getSudokuImage(size,false);
            assertSmallBaseGlyph(label+"-export",exported,digit,1);
            unchangedOutsideGlyph(label+"-export",exported,digit,1);
        }
        // Shared-node sectors retain one white glyph, with the same typographic rule.
        reset(size);node(5,0);node(5,3);BufferedImage shared=paint();
        assertSmallBaseGlyph("L-shared",shared,5,DENSITY);
        unchangedOutsideGlyph("L-shared",shared,5,DENSITY);
    }
    private static SolutionStep hint(String role) {
        SolutionStep result=new SolutionStep(SolutionType.NAKED_PAIR);
        if("placement".equals(role)){result.addIndex(CELL);result.addValue(DIGIT);}
        else if("delete".equals(role))result.addCandidateToDelete(CELL,DIGIT);
        else if("fin".equals(role))result.addFin(CELL,DIGIT);
        else if("endo-fin".equals(role))result.addEndoFin(CELL,DIGIT);
        else throw new IllegalArgumentException(role);
        return result;
    }
    /** Synthetic display ownership only; this does not simulate an analysis result. */
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void authoredDisplay(SolutionStep step,UserChain source) throws Exception {
        panel.setStep(step);
        Class<?> kindType=Class.forName("sudoku.SudokuPanel$ReasoningSourceKind");
        Object kind=Enum.valueOf((Class)kindType,"FREE_CHAIN");
        Class<?> requestType=Class.forName("sudoku.SudokuPanel$ReasoningRequest");
        Constructor<?> requestConstructor=requestType.getDeclaredConstructor(long.class,kindType,
                long.class,long.class,long.class,long.class,String.class,SudokuSet.class,UserChain.class);
        requestConstructor.setAccessible(true);
        Object request=requestConstructor.newInstance(1L,kind,0L,0L,0L,0L,
                TechniqueStepCatalog.createSignature(panel.getSudoku()),null,source);
        Class<?> proposalType=Class.forName("sudoku.SudokuPanel$ReasoningProposal");
        Constructor<?> proposalConstructor=proposalType.getDeclaredConstructor(requestType,
                NativeReasoningMatcher.NativeStepKey.class,long.class);
        proposalConstructor.setAccessible(true);
        Object proposal=proposalConstructor.newInstance(request,null,0L);
        Field display=proposalType.getDeclaredField("authoredChainDisplay");display.setAccessible(true);display.set(proposal,true);
        write("reasoningProposal",proposal);
    }
    private static void authoredFont(int size) throws Exception {
        for(String role:new String[]{"placement","delete"}) {
            reset(size);UserChain source=node(DIGIT,0);paint();
            SolutionStep step=hint(role);step.setAuthoredPlacement("placement".equals(role));
            authoredDisplay(step,source);BufferedImage image=paint();
            String label="authored-"+role;
            assertSmallBaseGlyph(label,image,DIGIT,DENSITY);
            unchangedOutsideGlyph(label,image,DIGIT,DENSITY);
            separateEndpointCue();
            for(AnnotationTool tool:TOOLS) {
                panel.setAnnotationTool(tool);panel.getCellZoomPanel().selectPaletteGroup(5);
                check(difference(image,paint(),cellArea(DENSITY),0)==0,
                        label+" typography or semantic color differs from M in "+tool);
            }
        }
    }
    private static void nativeUnchanged(int size) throws Exception {
        for(String role:new String[]{"placement","delete","fin","endo-fin"})for(boolean withL:new boolean[]{false,true}) {
            reset(size);if(withL){node(DIGIT,0);node(DIGIT,3);paint();}
            panel.setStep(hint(role));
            String label="native-"+role+(withL?"-L":"");
            unchanged(label,paint(),DENSITY);
            unchanged(label+"-export",panel.getSudokuImage(size,false),1);
        }
    }
    private static void focusConsistency(int size) throws Exception {
        for(String source:new String[]{"T-cell","T-candidate","L","P-circle","P-cross","P-shared-circle"}) {
            reset(size);
            if("T-cell".equals(source))colors("coloringMap").put(CELL,color(0));
            else if("T-candidate".equals(source))colors("coloringCandidateMap").put(CELL*10+DIGIT,color(0));
            else if("L".equals(source))node(DIGIT,0);
            else if("P-cross".equals(source))mark(DIGIT,DoodleStroke.MARK_FALSE_CROSS,0);
            else {mark(DIGIT,DoodleStroke.MARK_TRUE_CIRCLE,0);
                if("P-shared-circle".equals(source))mark(DIGIT,DoodleStroke.MARK_TRUE_CIRCLE,3);}
            if(!chains().isEmpty())separateEndpointCue();
            String data=fingerprint(),board=TechniqueStepCatalog.createSignature(panel.getSudoku());
            BufferedImage mouse=paint();save("focus-"+source+"-M",mouse,DENSITY);
            BufferedImage mouseExport=panel.getSudokuImage(size,false);
            for(AnnotationTool tool:TOOLS)for(int group:new int[]{4,5}) {
                panel.setAnnotationTool(tool);panel.getCellZoomPanel().selectPaletteGroup(group);
                BufferedImage current=paint();
                check(difference(mouse,current,cellArea(DENSITY),0)==0,
                        source+" noncurrent source differs from M in "+tool+" group "+group);
                BufferedImage exported=panel.getSudokuImage(size,false);
                check(difference(mouseExport,exported,cellArea(1),0)==0,
                        source+" export differs from M in "+tool+" group "+group);
            }
            panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
            check(difference(mouse,paint(),cellArea(DENSITY),0)==0,source+" M retains previous tool/group emphasis");
            check(data.equals(fingerprint()),source+" mode/group/export changed annotation data");
            check(board.equals(TechniqueStepCatalog.createSignature(panel.getSudoku())),source+" changed board data");
        }
    }
    private static void sharedSources(int size) throws Exception {
        reset(size);
        colors("coloringMap").put(CELL,color(0));colors("coloringCandidateMap").put(CELL*10+DIGIT,color(0));
        node(DIGIT,0);node(DIGIT,3);
        DoodleStroke a=mark(DIGIT,DoodleStroke.MARK_TRUE_CIRCLE,1),b=mark(DIGIT,DoodleStroke.MARK_TRUE_CIRCLE,2);
        DoodleStroke x=mark(DIGIT,DoodleStroke.MARK_FALSE_CROSS,1),y=mark(DIGIT,DoodleStroke.MARK_FALSE_CROSS,2);
        separateEndpointCue();
        String data=fingerprint();BufferedImage mouse=paint();save("shared-TLP-M",mouse,DENSITY);
        for(AnnotationTool tool:TOOLS) {
            panel.setAnnotationTool(tool);panel.getCellZoomPanel().selectPaletteGroup(5);
            BufferedImage full=paint();Rectangle area=candidateArea(DIGIT,DENSITY);
            check(difference(mouse,full,cellArea(DENSITY),0)==0,"shared T/L/P differs from M in "+tool);
            colors("coloringCandidateMap").remove(CELL*10+DIGIT);
            check(difference(full,paint(),area,25)>8,"shared "+tool+" loses T candidate perimeter");
            colors("coloringCandidateMap").put(CELL*10+DIGIT,color(0));
            List<UserChain> sources=new ArrayList<UserChain>(chains());
            for(int i=0;i<sources.size();i++) {
                if(!sources.get(i).getNodes().get(0).contains(CELL,DIGIT))continue;
                chains().remove(sources.get(i));
                check(difference(full,paint(),area,25)>8,"shared "+tool+" loses L source "+i);
                chains().clear();chains().addAll(sources);
            }
            for(DoodleStroke stroke:new DoodleStroke[]{a,b,x,y}) {
                ink().remove(stroke);
                check(difference(full,paint(),area,25)>5,"shared "+tool+" loses P source "+stroke.getCandidateMarkKind()+"/"+stroke.getThoughtGroup());
                ink().add(stroke);
            }
        }
        check(data.equals(fingerprint()),"shared source inspection changed annotation data");
    }
    private static void unrelatedUnchanged(int size) throws Exception {
        reset(size);colors("coloringMap").put(CELL,color(0));colors("coloringCandidateMap").put(CELL*10+DIGIT,color(0));
        unchanged("T-only-M",paint(),DENSITY);
        reset(size);mark(DIGIT,DoodleStroke.MARK_TRUE_CIRCLE,0);unchanged("P-circle-M",paint(),DENSITY);
        reset(size);
        DoodleStroke free=new DoodleStroke(Color.MAGENTA,.006f);
        free.getPoints().add(new DoodlePoint(.24,.28));free.getPoints().add(new DoodlePoint(.38,.36));
        free.getPoints().add(new DoodlePoint(.30,.43));
        DoodleStroke ellipse=new DoodleStroke(Color.CYAN,.004f);
        for(int i=0;i<=48;i++) {double angle=2*Math.PI*i/48;ellipse.getPoints().add(new DoodlePoint(.62+.08*Math.cos(angle),.64+.05*Math.sin(angle)));}
        ink().add(free);ink().add(ellipse);String data=fingerprint();
        for(AnnotationTool tool:TOOLS) {
            panel.setAnnotationTool(tool);panel.getCellZoomPanel().selectPaletteGroup(5);
            unchanged("free-ellipse-"+tool,paint(),DENSITY);
        }
        unchanged("free-ellipse-export",panel.getSudokuImage(size,false),1);
        check(data.equals(fingerprint()),"freehand/ellipse data changed during paint/export/mode switch");
    }
    private static String fingerprint() throws Exception {
        StringBuilder text=new StringBuilder().append(colors("coloringMap")).append(colors("coloringCandidateMap"));
        // Source ordering is not part of this fixture's deliberate remove/restore comparisons.
        List<String> strokes=new ArrayList<String>();
        for(DoodleStroke stroke:ink()) {
            StringBuilder s=new StringBuilder().append(stroke.getColor().getRGB()).append('|').append(stroke.getWidthFactor())
                    .append('|').append(stroke.getAnchorCell()).append('|').append(stroke.getAnchorDigit())
                    .append('|').append(stroke.getCandidateMarkKind()).append('|').append(stroke.getThoughtGroup())
                    .append('|').append(stroke.isHypothesisStart()).append('|').append(stroke.isConclusionOutlined());
            for(DoodlePoint point:stroke.getPoints())s.append('|').append(point.getX()).append(',').append(point.getY());
            strokes.add(s.toString());
        }
        java.util.Collections.sort(strokes);text.append(strokes);
        for(UserChain chain:chains()) {
            for(UserChainNode node:chain.getNodes())text.append(node.key()).append(node.getColor());
            text.append(chain.getStrongRelations()).append(chain.getRelationColors()).append(chain.isClosed());
        }
        return text.toString();
    }
}
