package local.smartframe.transfer;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public final class Ui {
    public static final int INK=Color.rgb(29,46,50), ACCENT=Color.rgb(21,102,98), PAPER=Color.rgb(245,247,244);
    public static int dp(Context c,int n) { return (int)(c.getResources().getDisplayMetrics().density*n); }
    public static LinearLayout column(Context c) {
        LinearLayout v=new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(c,20),dp(c,14),dp(c,20),dp(c,14)); v.setBackgroundColor(PAPER); return v;
    }
    public static TextView text(Context c,String s,int size) {
        TextView v=new TextView(c); v.setText(s); v.setTextSize(size); v.setTextColor(INK); v.setPadding(0,dp(c,5),0,dp(c,5)); return v;
    }
    public static Button button(Context c,LinearLayout parent,String label,Runnable run) {
        Button b=new Button(c); b.setText(label); b.setTextSize(17); b.setAllCaps(false); b.setTextColor(ACCENT); parent.addView(b,new LinearLayout.LayoutParams(-1,dp(c,54))); b.setOnClickListener(v->run.run()); return b;
    }
    public static EditText input(Context c,LinearLayout parent,String hint,int type) {
        EditText e=new EditText(c); e.setSingleLine(true); e.setTextSize(18); e.setHint(hint); e.setInputType(type); parent.addView(e,new LinearLayout.LayoutParams(-1,dp(c,58))); return e;
    }
    public static void message(Activity a,String title,String text) { new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton("확인",null).show(); }
    public static void toast(Context c,String s) { Toast.makeText(c,s,Toast.LENGTH_LONG).show(); }
    public static String error(Throwable e) { String s=e.getMessage(); return s==null ? "연결을 확인한 뒤 다시 시도해 주세요." : s; }
}
