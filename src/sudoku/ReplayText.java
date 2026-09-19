package sudoku;
import java.text.MessageFormat;
import java.util.ResourceBundle;
/** Replay uses the existing MainFrame locale contract. */
public final class ReplayText {
 private ReplayText(){}
 public static String text(String key,Object...args){return MessageFormat.format(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.replay."+key),args);}
 public static String time(long milliseconds){long s=Math.max(0,milliseconds)/1000;return String.format("%02d:%02d:%02d",s/3600,(s/60)%60,s%60);}
}
