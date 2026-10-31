package local.smartframe.sender;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.LruCache;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import local.smartframe.transfer.Ui;
import local.smartframe.transfer.core.PhotoSelection;

/** Local gallery: single taps select/deselect; no long-press selection mode. */
public final class GalleryActivity extends Activity {
    static final String RESULT_PHOTOS="selected_photos",LIMIT="selection_limit";
    private static final int PERMISSION=41;
    private static final class Photo {
        final long id;final String uri,name;
        Photo(long id,String name){this.id=id;uri=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id).toString();this.name=name==null?"사진":name;}
    }
    private final ArrayList<Photo> photos=new ArrayList<>();
    private final ExecutorService queryWorker=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor thumbnailWorker=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){protected int sizeOf(String key,Bitmap value){return value.getByteCount();}};
    private final Set<CancellationSignal> thumbnailSignals=Collections.synchronizedSet(new HashSet<>());
    private PhotoSelection selected;private int limit,generation;private volatile boolean closed;
    private CancellationSignal querySignal;private boolean loading,loaded;
    private TextView status,empty;private Button add,permissionButton;private GridView grid;private GridAdapter adapter;
    public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setStatusBarColor(Ui.ACCENT);
        limit=Math.max(1,Math.min(200,getIntent().getIntExtra(LIMIT,200)));selected=new PhotoSelection(limit);
        if(saved!=null){ArrayList<String> values=saved.getStringArrayList(RESULT_PHOTOS);if(values!=null)selected.restore(values);}
        LinearLayout root=Ui.column(this);setContentView(root);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);root.addView(bar);
        TextView title=Ui.text(this,"사진 고르기",26);title.setTypeface(null,Typeface.BOLD);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button cancel=button("취소",false);bar.addView(cancel,new LinearLayout.LayoutParams(-2,-2));cancel.setOnClickListener(v->finish());
        root.addView(Ui.text(this,"사진을 톡톡 눌러서 골라 주세요",19));
        status=Ui.text(this,"사진을 불러오는 중…",18);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status);
        FrameLayout area=new FrameLayout(this);root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(Ui.dp(this,100));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(Ui.dp(this,6));grid.setVerticalSpacing(Ui.dp(this,6));
        grid.setLongClickable(false);adapter=new GridAdapter();grid.setAdapter(adapter);area.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        empty=Ui.text(this,"",20);empty.setGravity(Gravity.CENTER);area.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        grid.setOnItemClickListener((parent,view,position,id)->{
            if(loading||position>=photos.size())return;
            if(selected.toggle(photos.get(position).uri)==PhotoSelection.Change.FULL){Ui.toast(this,"지금은 "+limit+"장까지 고를 수 있어요.");return;}
            adapter.notifyDataSetChanged();updateSelection();
        });
        permissionButton=button("사진 접근 허용",false);root.addView(permissionButton,new LinearLayout.LayoutParams(-1,-2));permissionButton.setVisibility(View.GONE);permissionButton.setOnClickListener(v->requestAccess());
        add=button("선택한 사진 추가",true);root.addView(add,new LinearLayout.LayoutParams(-1,-2));add.setEnabled(false);
        add.setOnClickListener(v->{if(loading||selected.size()==0)return;add.setEnabled(false);setResult(RESULT_OK,new Intent().putStringArrayListExtra(RESULT_PHOTOS,selected.values()));finish();});
    }
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setTextSize(21);b.setAllCaps(false);b.setMinHeight(Ui.dp(this,64));b.setMinimumHeight(Ui.dp(this,64));b.setPadding(Ui.dp(this,14),Ui.dp(this,10),Ui.dp(this,14),Ui.dp(this,10));
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Color.DKGRAY,primary?Color.WHITE:Ui.ACCENT}));
        b.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Color.LTGRAY,primary?Ui.ACCENT:Color.WHITE}));return b;
    }
    // targetSdk 29 uses READ_EXTERNAL_STORAGE, including Android 13's compatibility path.
    private boolean hasAccess(){return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}
    protected void onResume(){super.onResume();if(hasAccess()){permissionButton.setVisibility(View.GONE);if(!loaded&&!loading)loadPhotos();}else showPermission();}
    private void showPermission(){
        generation++;if(querySignal!=null)querySignal.cancel();loading=false;loaded=false;photos.clear();adapter.notifyDataSetChanged();add.setEnabled(false);
        status.setText("사진을 보려면 접근 허용이 필요해요");empty.setText("휴대폰에 저장된 사진을 보여드릴게요.\n고른 사진만 전송 목록에 추가합니다.");
        boolean asked=getPreferences(0).getBoolean("asked",false);
        permissionButton.setText(asked&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)?"설정에서 사진 접근 허용":"사진 접근 허용");permissionButton.setVisibility(View.VISIBLE);
    }
    private void requestAccess(){
        if(hasAccess()){permissionButton.setVisibility(View.GONE);loadPhotos();return;}
        if(getPreferences(0).getBoolean("asked",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)){
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){Ui.toast(this,"휴대폰 설정에서 이 앱의 사진 접근을 허용해 주세요.");}return;
        }
        getPreferences(0).edit().putBoolean("asked",true).apply();requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION);
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request!=PERMISSION)return;if(hasAccess()){permissionButton.setVisibility(View.GONE);if(!loading)loadPhotos();}else showPermission();}
    private void loadPhotos(){
        if(loading||closed)return;loading=true;final int current=++generation;
        final CancellationSignal signal=new CancellationSignal();querySignal=signal;
        status.setText("사진을 불러오는 중…");empty.setText("사진을 불러오고 있어요…");add.setEnabled(false);permissionButton.setVisibility(View.GONE);
        queryWorker.execute(()->{
            ArrayList<Photo> found=new ArrayList<>();String failure=null;
            // Read metadata only; decode thumbnails on demand with a bounded cache.
            String[] types={"image/jpeg","image/png","image/webp","image/heic","image/heif"};
            try(Cursor c=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id","_display_name"},"mime_type IN (?,?,?,?,?)",types,"date_added DESC, _id DESC",signal)){
                if(c==null)throw new IllegalStateException("No media response");
                while(c.moveToNext()){signal.throwIfCanceled();found.add(new Photo(c.getLong(0),c.getString(1)));}
            }catch(OperationCanceledException cancelled){return;}catch(Exception e){failure="사진을 불러오지 못했어요. 사진 접근 권한을 확인한 뒤 다시 열어 주세요.";}
            final String error=failure;
            runOnUiThread(()->{
                if(closed||current!=generation)return;loading=false;loaded=error==null;
                if(error!=null){if(!hasAccess()){showPermission();return;}status.setText(error);empty.setText("다시 시도하려면 아래 버튼을 눌러 주세요.");permissionButton.setText("다시 불러오기");permissionButton.setVisibility(View.VISIBLE);return;}
                photos.clear();photos.addAll(found);HashSet<String> visible=new HashSet<>();for(Photo p:photos)visible.add(p.uri);selected.retain(visible);
                empty.setText("휴대폰에 저장된 사진이 없어요.\n클라우드에만 있는 사진은 먼저 휴대폰에 내려받아 주세요.");adapter.notifyDataSetChanged();updateSelection();
            });
        });
    }
    private void updateSelection(){status.setText("고른 사진 "+selected.size()+"장 · 최대 "+limit+"장");add.setText(selected.size()==0?"사진을 골라 주세요":"선택한 사진 "+selected.size()+"장 추가");add.setEnabled(!loading&&selected.size()>0);}
    private final class Cell extends FrameLayout {
        final ImageView image;final TextView mark;String uri;CancellationSignal signal;
        Cell(){super(GalleryActivity.this);setPadding(Ui.dp(getContext(),3),Ui.dp(getContext(),3),Ui.dp(getContext(),3),Ui.dp(getContext(),3));
            image=new ImageView(getContext());image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);addView(image,new FrameLayout.LayoutParams(-1,-1));
            mark=Ui.text(getContext(),"",23);mark.setGravity(Gravity.CENTER);mark.setTextColor(Color.WHITE);mark.setPadding(0,0,0,0);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Ui.dp(getContext(),38),Ui.dp(getContext(),38),Gravity.TOP|Gravity.RIGHT);lp.setMargins(0,Ui.dp(getContext(),5),Ui.dp(getContext(),5),0);addView(mark,lp);
        }
        protected void onMeasure(int widthSpec,int heightSpec){super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec),MeasureSpec.EXACTLY));}
    }
    private final class GridAdapter extends BaseAdapter {
        public int getCount(){return photos.size();}public Object getItem(int p){return photos.get(p);}public long getItemId(int p){return photos.get(p).id;}public boolean hasStableIds(){return true;}
        public View getView(int position,View old,ViewGroup parent){
            Cell cell=old instanceof Cell?(Cell)old:new Cell();Photo p=photos.get(position);
            boolean checked=selected.contains(p.uri);cell.setSelected(checked);cell.setContentDescription(p.name+(checked?", 선택됨. 누르면 선택 해제":", 누르면 선택"));
            GradientDrawable border=new GradientDrawable();border.setColor(checked?Ui.ACCENT:Color.WHITE);cell.setBackground(border);
            GradientDrawable badge=new GradientDrawable();badge.setShape(GradientDrawable.OVAL);badge.setColor(checked?Ui.ACCENT:Color.argb(130,0,0,0));badge.setStroke(Ui.dp(GalleryActivity.this,2),Color.WHITE);cell.mark.setBackground(badge);cell.mark.setText(checked?"✓":"");
            if(!p.uri.equals(cell.uri)){if(cell.signal!=null)cell.signal.cancel();cell.uri=p.uri;cell.image.setImageResource(android.R.drawable.ic_menu_gallery);}
            Bitmap bitmap=cache.get(p.uri);if(bitmap!=null){cell.image.setImageBitmap(bitmap);return cell;}
            if(cell.signal!=null)cell.signal.cancel();final CancellationSignal signal=new CancellationSignal();cell.signal=signal;
            thumbnailWorker.execute(()->{
                if(closed||signal.isCanceled())return;thumbnailSignals.add(signal);
                try{
                    Bitmap ready;
                    if(Build.VERSION.SDK_INT>=29)ready=getContentResolver().loadThumbnail(Uri.parse(p.uri),new Size(256,256),signal);
                    else ready=MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(),p.id,MediaStore.Images.Thumbnails.MINI_KIND,null);
                    if(closed||signal.isCanceled()||ready==null)return;cache.put(p.uri,ready);
                    runOnUiThread(()->{if(!closed&&!signal.isCanceled()&&p.uri.equals(cell.uri))cell.image.setImageBitmap(ready);});
                }catch(Exception ignored){}finally{thumbnailSignals.remove(signal);}
            });
            return cell;
        }
    }
    protected void onSaveInstanceState(Bundle state){state.putStringArrayList(RESULT_PHOTOS,selected.values());super.onSaveInstanceState(state);}
    protected void onDestroy(){closed=true;if(querySignal!=null)querySignal.cancel();synchronized(thumbnailSignals){for(CancellationSignal s:thumbnailSignals)s.cancel();}queryWorker.shutdownNow();thumbnailWorker.shutdownNow();cache.evictAll();super.onDestroy();}
}
