package local.smartframe.sender;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import local.smartframe.transfer.Ui;
import local.smartframe.transfer.core.PhotoSelection;

/** Authenticated remote gallery. It never deletes phone media or invokes phone media delete APIs. */
public final class FramePhotosActivity extends Activity {
    private static final class Item {String hash,name;Item(JSONObject j)throws Exception{hash=j.getString("hash");name=j.optString("name","사진");}}
    private SenderDb db;private SenderDb.Frame frame;private String volume,storage="";
    private final ArrayList<Item> items=new ArrayList<>();private final ArrayList<JSONObject> pages=new ArrayList<>();
    private JSONObject nextCursor;private int page;private boolean busy,deleting;private volatile boolean closed;
    private final PhotoSelection selection=new PhotoSelection(50);
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor thumbnails=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private TextView status,empty;private Button previous,next,refresh,remove;private GridView grid;private Adapter adapter;
    public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setStatusBarColor(Ui.ACCENT);db=new SenderDb(this);frame=db.frame(getIntent().getStringExtra("frame"));
        if(frame==null){Ui.toast(this,"액자를 먼저 연결해 주세요.");finish();return;}
        LinearLayout root=Ui.column(this);setContentView(root);button(root,"‹ 설정으로 돌아가기",()->onBackPressed());
        TextView title=Ui.text(this,"액자 사진 관리",26);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(Ui.text(this,"이 앱으로 액자에 보낸 사진만 표시해요.\n휴대폰 원본은 삭제하지 않습니다.",18));
        status=Ui.text(this,"",18);root.addView(status);
        FrameLayout area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(Ui.dp(this,105));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(Ui.dp(this,6));grid.setVerticalSpacing(Ui.dp(this,6));grid.setLongClickable(false);
        adapter=new Adapter();grid.setAdapter(adapter);area.addView(grid,new FrameLayout.LayoutParams(-1,-1));empty=Ui.text(this,"사진을 불러오고 있어요…",20);empty.setGravity(Gravity.CENTER);area.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        grid.setOnItemClickListener((p,v,position,id)->{if(busy)return;if(selection.toggle(items.get(position).hash)==PhotoSelection.Change.FULL)Ui.toast(this,"한 번에 50장까지 삭제할 수 있어요.");adapter.notifyDataSetChanged();paint();});
        LinearLayout nav=new LinearLayout(this);root.addView(nav);previous=navButton(nav,"이전",()->load(page-1,false));next=navButton(nav,"다음",()->load(page+1,false));refresh=navButton(nav,"새로고침",()->load(0,true));
        remove=button(root,"선택한 액자 사진 삭제",()->confirmDelete());remove.setTextColor(Color.rgb(165,45,35));
        pages.add(new JSONObject());load(0,true);
    }
    private Button makeButton(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(20);b.setMinHeight(Ui.dp(this,60));b.setPadding(Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10));b.setTextColor(Ui.ACCENT);b.setOnClickListener(v->action.run());return b;}
    private Button button(LinearLayout root,String label,Runnable action){Button b=makeButton(label,action);root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private Button navButton(LinearLayout root,String label,Runnable action){Button b=makeButton(label,action);root.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
    private void paint(){
        previous.setEnabled(!busy&&page>0);next.setEnabled(!busy&&nextCursor!=null);refresh.setEnabled(!busy);remove.setEnabled(!busy&&selection.size()>0);grid.setEnabled(!busy);
        remove.setText(selection.size()==0?"지울 사진을 눌러 주세요":"액자 사진 "+selection.size()+"장 삭제");
        if(!busy)status.setText(frame.name+" · "+storage+"\n"+(page+1)+"쪽 · 선택 "+selection.size()+"장");
    }
    private void load(int target,boolean reset){
        if(busy||closed||target<0)return;
        if(reset){pages.clear();pages.add(new JSONObject());selection.restore(Collections.emptyList());volume=null;nextCursor=null;page=0;items.clear();adapter.notifyDataSetChanged();cache.evictAll();}
        else if(target>=pages.size()){if(nextCursor==null)return;pages.add(nextCursor);}
        final JSONObject request=pages.get(target);busy=true;status.setText("액자에서 사진을 불러오는 중…");paint();
        work.execute(()->{try{
            if(reset){String found=LocalDiscovery.find(this,frame.id);if(found!=null)frame.host=found;}
            JSONObject result=Api.framePhotos(frame,request);String targetVolume=result.getString("volume");ArrayList<Item> loaded=new ArrayList<>();JSONArray list=result.getJSONArray("photos");for(int i=0;i<list.length();i++)loaded.add(new Item(list.getJSONObject(i)));
            JSONObject cursor=result.optBoolean("more")?new JSONObject().put("volume",targetVolume).put("before",result.getLong("before")).put("beforeHash",result.getString("beforeHash")):null;
            db.address(frame.id,frame.host);
            runOnUiThread(()->{if(closed)return;volume=targetVolume;storage=result.optString("storage","액자 저장소");page=target;nextCursor=cursor;items.clear();items.addAll(loaded);busy=false;empty.setText("이 저장소에 표시할 사진이 없어요.");adapter.notifyDataSetChanged();paint();});
        }catch(Exception e){runOnUiThread(()->{if(closed)return;busy=false;paint();status.setText("사진 목록을 불러오지 못했어요");empty.setText("같은 와이파이와 수신 앱 실행 상태를 확인해 주세요.");showMessage("액자 사진을 확인하지 못했어요",Ui.error(e)+"\n\n이 기능은 수신 앱 0.1.5 이상이 필요합니다.");});}});
    }
    private void confirmDelete(){
        if(busy||selection.size()==0||volume==null)return;
        final ArrayList<String> hashes=selection.values();final String selectedVolume=volume;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("액자 사진 "+hashes.size()+"장을 삭제할까요?")
            .setMessage(frame.name+" · "+storage+"\n\n선택한 사진을 액자 저장소에서 삭제합니다. 휴대폰 원본은 그대로 남아요. 다시 액자에서 보려면 사진을 다시 보내 주세요.")
            .setNegativeButton("취소",null).setPositiveButton("액자에서 삭제",(d,w)->delete(hashes,selectedVolume)).create();showLarge(dialog);
    }
    private void delete(ArrayList<String> hashes,String selectedVolume){
        if(busy)return;busy=true;deleting=true;status.setText("선택한 액자 사진을 삭제하는 중…");paint();
        work.execute(()->{try{
            JSONObject result=Api.deleteFramePhotos(frame,selectedVolume,hashes);JSONArray deleted=result.getJSONArray("deleted"),missing=result.getJSONArray("missing");JSONObject failed=result.getJSONObject("failed");
            ArrayList<String> confirmed=new ArrayList<>();for(int i=0;i<deleted.length();i++)confirmed.add(deleted.getString(i));for(int i=0;i<missing.length();i++)confirmed.add(missing.getString(i));confirmed.retainAll(hashes);db.remoteDeleted(frame.id,confirmed);
            try{db.archiveDone(frame.id);}catch(Exception e){android.util.Log.w("FrameSend","Completed staging cleanup deferred",e);}
            String text="액자에서 삭제 "+deleted.length()+"장"+(missing.length()>0?"\n이미 없는 사진 "+missing.length()+"장":"")+(failed.length()>0?"\n삭제하지 못한 사진 "+failed.length()+"장":"")+"\n휴대폰 원본은 그대로입니다.";
            if(failed.length()>0){Iterator<String> keys=failed.keys();if(keys.hasNext())text+="\n\n"+failed.optString(keys.next());}
            final String report=text;runOnUiThread(()->{if(closed)return;busy=false;deleting=false;load(0,true);showMessage("액자 사진 삭제 결과",report);});
        }catch(Exception e){runOnUiThread(()->{if(closed)return;busy=false;deleting=false;paint();showMessage("삭제 결과를 확인하지 못했어요",Ui.error(e)+"\n\n일부 사진이 삭제됐을 수 있어요. ‘새로고침’으로 액자 목록을 확인해 주세요.");});}});
    }
    private void showLarge(AlertDialog d){d.show();for(int id:new int[]{-1,-2}){Button b=d.getButton(id);if(b!=null){b.setTextSize(20);b.setMinHeight(Ui.dp(this,56));}}TextView message=d.findViewById(android.R.id.message);if(message!=null)message.setTextSize(19);}
    private void showMessage(String title,String text){showLarge(new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("확인",null).create());}
    private final class Cell extends FrameLayout {
        final ImageView image;final TextView mark;String key;
        Cell(){super(FramePhotosActivity.this);setPadding(3,3,3,3);image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.CENTER_CROP);addView(image,new FrameLayout.LayoutParams(-1,-1));mark=Ui.text(getContext(),"",24);mark.setGravity(Gravity.CENTER);mark.setTextColor(Color.WHITE);mark.setPadding(0,0,0,0);addView(mark,new FrameLayout.LayoutParams(Ui.dp(getContext(),38),Ui.dp(getContext(),38),Gravity.TOP|Gravity.RIGHT));image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);mark.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(w),MeasureSpec.EXACTLY));}
    }
    private final class Adapter extends BaseAdapter {
        public int getCount(){return items.size();}public Object getItem(int p){return items.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View old,ViewGroup parent){
            Item item=items.get(p);Cell cell=old instanceof Cell?(Cell)old:new Cell();final String target=volume,key=target+":"+item.hash;
            boolean checked=selection.contains(item.hash);cell.setBackgroundColor(checked?Color.rgb(165,45,35):Color.WHITE);GradientDrawable badge=new GradientDrawable();badge.setShape(GradientDrawable.OVAL);badge.setColor(checked?Color.rgb(165,45,35):Color.argb(140,0,0,0));badge.setStroke(2,Color.WHITE);cell.mark.setBackground(badge);cell.mark.setText(checked?"✓":"");cell.setContentDescription(item.name+(checked?", 삭제 선택됨":", 누르면 삭제할 사진으로 선택"));
            Bitmap bitmap=cache.get(key);if(key.equals(cell.key)&&bitmap!=null){cell.image.setImageBitmap(bitmap);return cell;}cell.key=key;cell.image.setImageResource(android.R.drawable.ic_menu_gallery);
            if(bitmap!=null){cell.image.setImageBitmap(bitmap);return cell;}
            thumbnails.execute(()->{if(closed||!key.equals(cell.key))return;try{byte[] bytes=Api.frameThumbnail(frame,target,item.hash);Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(image==null||closed)return;cache.put(key,image);runOnUiThread(()->{if(!closed&&key.equals(cell.key))cell.image.setImageBitmap(image);});}catch(Exception ignored){}});return cell;
        }
    }
    public void onBackPressed(){if(deleting){Ui.toast(this,"삭제 결과를 확인하고 있어요. 잠시 기다려 주세요.");return;}super.onBackPressed();}
    protected void onDestroy(){closed=true;work.shutdown();thumbnails.shutdownNow();cache.evictAll();super.onDestroy();}
}
