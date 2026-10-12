/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

/** Small foreground-driven vector family for replay navigation and record state. */
final class ReplayIcon implements Icon {
    enum Kind { FIRST, PREVIOUS_GROUP, PREVIOUS, PLAY, PAUSE, NEXT, NEXT_GROUP, LAST, PIN, OPEN }
    private final Kind kind;
    private final int size;
    private final boolean active;
    ReplayIcon(Kind kind,int size){this(kind,size,false);}
    ReplayIcon(Kind kind,int size,boolean active){this.kind=kind;this.size=size;this.active=active;}
    public int getIconWidth(){return size;}
    public int getIconHeight(){return size;}
    public void paintIcon(Component component,Graphics graphics,int x,int y){
        Graphics2D g=(Graphics2D)graphics.create();
        try{
            g.translate(x,y);g.scale(size/20.0,size/20.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.getForeground());
            if(!component.isEnabled())g.setComposite(AlphaComposite.SrcOver.derive(.38f));
            g.setStroke(new BasicStroke(1.5f*20/size,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            switch(kind){
            case PLAY:g.fill(path(7,4,16,10,7,16));break;
            case PAUSE:g.fill(new RoundRectangle2D.Double(6,4,3,12,1,1));g.fill(new RoundRectangle2D.Double(12,4,3,12,1,1));break;
            case PREVIOUS:chevron(g,13,7);break;
            case NEXT:chevron(g,7,13);break;
            case PREVIOUS_GROUP:chevron(g,9,4);chevron(g,16,11);break;
            case NEXT_GROUP:chevron(g,4,9);chevron(g,11,16);break;
            case FIRST:g.draw(new Line2D.Double(4,4,4,16));chevron(g,14,8);break;
            case LAST:chevron(g,6,12);g.draw(new Line2D.Double(16,4,16,16));break;
            case PIN:
                Shape body=path(6,3,14,3,12.5,9,16,12,4,12,7.5,9);
                if(active)g.fill(body);else g.draw(body);
                g.draw(new Line2D.Double(10,12,10,18));break;
            case OPEN:g.draw(path(3,6,8,6,10,8,17,8,17,16,3,16));break;
            default:break;
            }
        }finally{g.dispose();}
    }
    private static void chevron(Graphics2D g,double edge,double tip){Path2D p=new Path2D.Double();p.moveTo(edge,5);p.lineTo(tip,10);p.lineTo(edge,15);g.draw(p);}
    private static Shape path(double... points){Path2D p=new Path2D.Double();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);p.closePath();return p;}
}
