package local.smartframe.sender;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.Inet4Address;
import java.util.*;
import java.util.concurrent.*;
import local.smartframe.transfer.Ui;
import local.smartframe.transfer.core.Wire;

public final class SenderActivity extends Activity {
    private SenderDb db;private Spinner frames;private TextView summary,connection,empty,settingsConnection;
    private Button send,choose,check,connectButton,clearButton;private PhotosAdapter adapter;private Dialog settings,historyDialog;
    private List<SenderDb.Frame> frameList=new ArrayList<>();private List<SenderDb.Photo> rows=new ArrayList<>();
    private final Handler handler=new Handler();private final ExecutorService work=Executors.newSingleThreadExecutor();private final ExecutorService thumbs=Executors.newFixedThreadPool(2);
    private final LruCache<String,Bitmap> thumbnails=new LruCache<>(40);
    private boolean importing,starting;private String importStatus="",chosenId="";
    private final ArrayList<Uri> pendingShares=new ArrayList<>();private List<Uri> activeShare=Collections.emptyList();
    private NsdManager nsd;private NsdManager.DiscoveryListener discovery;private WifiManager.MulticastLock multicast;
    private final Map<String,String> found=new LinkedHashMap<>();private final ArrayDeque<NsdServiceInfo> resolveQueue=new ArrayDeque<>();private boolean resolving;
    private final Runnable tick=new Runnable(){public void run(){drainShares();refresh();handler.postDelayed(this,1200);}};
    public void onCreate(Bundle state){
        super.onCreate(state);db=new SenderDb(this);if(!TransferService.running)db.interrupted();
        chosenId=getPreferences(0).getString("frame","");if(!TransferService.running)db.archiveDone(chosenId);getWindow().setStatusBarColor(Ui.ACCENT);
        LinearLayout root=Ui.column(this);setContentView(root);
        // The header scrolls with photos, so large fonts/small landscape screens retain access.
        ListView list=new ListView(this);list.setDividerHeight(Ui.dp(this,10));list.setBackgroundColor(Ui.PAPER);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);header.addView(top);
        TextView heading=Ui.text(this,"사진 보내기",28);heading.setTypeface(null,Typeface.BOLD);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button settingsButton=new Button(this);styleButton(settingsButton,false);settingsButton.setText("⚙ 설정");settingsButton.setContentDescription("설정: 액자 연결, 전송 기록, 저장 여부 확인");top.addView(settingsButton,new LinearLayout.LayoutParams(-2,-2));settingsButton.setOnClickListener(v->showSettings());
        connection=Ui.text(this,"",20);header.addView(connection);
        choose=largeButton(header,"＋ 사진 고르기",false,()->pick());
        summary=Ui.text(this,"사진을 골라 주세요",20);summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);header.addView(summary);
        empty=Ui.text(this,"보내고 싶은 사진을 골라 주세요.\n여러 장을 한 번에 고를 수 있어요.",20);empty.setGravity(Gravity.CENTER);empty.setPadding(0,Ui.dp(this,32),0,Ui.dp(this,32));header.addView(empty);
        list.addHeaderView(header,null,false);adapter=new PhotosAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        send=largeButton(root,"사진 보내기",true,()->startTransfer(false));
        loadFrames();
        if(state==null)receiveShare(getIntent());else{ArrayList<Uri> saved=state.getParcelableArrayList("sharedPhotos");if(saved!=null)pendingShares.addAll(saved);drainShares();}
    }
    protected void onNewIntent(Intent intent){super.onNewIntent(intent);receiveShare(intent);}
    private void receiveShare(Intent intent){
        if(!SharedPhotos.accepts(intent))return;
        try{
            ArrayList<Uri> incoming=SharedPhotos.read(intent);
            LinkedHashSet<Uri> combined=new LinkedHashSet<>(pendingShares);combined.addAll(incoming);
            if(combined.size()+activeShare.size()>200)throw new IllegalArgumentException("준비 중인 사진이 많아요. 준비가 끝나면 다시 공유해 주세요.");
            pendingShares.clear();pendingShares.addAll(combined);
            if(settings!=null)settings.dismiss();if(historyDialog!=null)historyDialog.dismiss();
            if(importing||starting||TransferService.running)Ui.toast(this,"현재 작업이 끝나면 공유한 사진을 준비할게요.");
            drainShares();
        }catch(RuntimeException e){message("사진을 가져오지 못했어요",Ui.error(e));}
        // Do not replay the original share when a consumed activity is recreated.
        setIntent(new Intent(this,SenderActivity.class).setAction(Intent.ACTION_MAIN));
    }
    private void drainShares(){
        if(pendingShares.isEmpty()||importing||starting||TransferService.running||isFinishing()||isDestroyed())return;
        ArrayList<Uri> incoming=new ArrayList<>(pendingShares);pendingShares.clear();activeShare=incoming;
        importing=true;importStatus="공유한 사진을 준비하고 있어요…";refresh();work.execute(()->importPhotos(incoming));
    }
    protected void onSaveInstanceState(Bundle state){ArrayList<Uri> saved=new ArrayList<>(activeShare);saved.addAll(pendingShares);state.putParcelableArrayList("sharedPhotos",saved);super.onSaveInstanceState(state);}
    public void onBackPressed(){if(importing||!pendingShares.isEmpty()){Ui.toast(this,"공유한 사진 준비가 끝난 뒤 닫아 주세요.");return;}super.onBackPressed();}
    private void styleButton(Button b,boolean primary){
        b.setAllCaps(false);b.setTextSize(22);b.setMinHeight(Ui.dp(this,68));b.setMinimumHeight(Ui.dp(this,68));b.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Color.rgb(96,105,106),primary?Color.WHITE:Ui.ACCENT}));
        b.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{Color.rgb(224,228,225),primary?Ui.ACCENT:Color.WHITE}));
    }
    private Button largeButton(LinearLayout parent,String label,boolean primary,Runnable action){Button b=new Button(this);styleButton(b,primary);b.setText(label);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(this,6);parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    private void showSettings(){
        if(settings!=null&&settings.isShowing())return;
        settings=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);
        ScrollView scroll=new ScrollView(this);LinearLayout col=Ui.column(this);scroll.addView(col);settings.setContentView(scroll);
        largeButton(col,"‹ 사진으로 돌아가기",false,()->settings.dismiss());
        TextView heading=Ui.text(this,"설정",28);heading.setTypeface(null,Typeface.BOLD);col.addView(heading);
        col.addView(Ui.text(this,"사진을 보낼 액자",20));
        frames=new Spinner(this);frames.setMinimumHeight(Ui.dp(this,64));col.addView(frames,new LinearLayout.LayoutParams(-1,-2));
        settingsConnection=Ui.text(this,"",18);col.addView(settingsConnection);
        frames.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position<frameList.size()&&!chosenId.equals(frameList.get(position).id)){chosenId=frameList.get(position).id;getPreferences(0).edit().putString("frame",chosenId).apply();TransferService.message="";refresh();}}});
        connectButton=largeButton(col,"액자 연결 · 변경",false,()->{if(!busy())pairDialog();});
        largeButton(col,"전송 기록 보기",false,()->showHistory());
        largeButton(col,"액자 사진 관리 · 삭제",false,()->{if(busy())return;if(db.frame(chosenId)==null){message("액자를 먼저 연결해 주세요","‘액자 연결 · 변경’에서 액자를 등록해 주세요.");return;}startActivity(new Intent(this,FramePhotosActivity.class).putExtra("frame",chosenId));});
        check=largeButton(col,"액자에 저장됐는지 확인",false,()->{if(busy())return;settings.dismiss();startTransfer(true);});
        col.addView(Ui.text(this,"지금 고른 사진이 액자에 있는지 확인해요. 사진을 보낼 때도 자동으로 확인합니다.",18));
        clearButton=largeButton(col,"고른 사진 목록 비우기",false,()->clearSelection());
        col.addView(Ui.text(this,"목록만 비웁니다. 휴대폰 원본과 액자 사진은 그대로 남아요.",18));
        col.addView(Ui.text(this,"액자에 사진 보내기 · 0.5.0",16));
        settings.setOnDismissListener(d->{frames=null;settingsConnection=null;check=null;connectButton=null;clearButton=null;settings=null;});
        loadFrames();settings.show();settings.getWindow().setLayout(-1,-1);
    }
    private void clearSelection(){if(busy())return;AlertDialog dialog=new AlertDialog.Builder(this).setTitle("고른 사진 목록을 비울까요?").setMessage("휴대폰 원본, 액자 사진, 전송 기록은 그대로 남아요.").setNegativeButton("취소",null).setPositiveButton("목록 비우기",(d,w)->{if(busy())return;db.clearSelection();thumbnails.evictAll();TransferService.message="";refresh();}).create();showLargeDialog(dialog);}
    private void showHistory(){
        final String frame=chosenId;final int[] page={0};
        LinearLayout col=Ui.column(this);TextView pageLabel=Ui.text(this,"",19);col.addView(pageLabel);
        ScrollView scroll=new ScrollView(this);TextView body=Ui.text(this,"",20);body.setTextIsSelectable(true);scroll.addView(body);col.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);col.addView(nav);Button previous=new Button(this),next=new Button(this);styleButton(previous,false);styleButton(next,false);previous.setText("이전");next.setText("다음");nav.addView(previous,new LinearLayout.LayoutParams(0,-2,1));nav.addView(next,new LinearLayout.LayoutParams(0,-2,1));
        Runnable render=()->{int count=db.historyCount(frame),pages=Math.max(1,(count+49)/50);page[0]=Math.min(page[0],pages-1);pageLabel.setText("전체 "+count+"건 · "+(page[0]+1)+" / "+pages+"쪽");body.setText(db.history(frame,page[0]));previous.setEnabled(page[0]>0);next.setEnabled(page[0]+1<pages);scroll.scrollTo(0,0);};
        previous.setOnClickListener(v->{page[0]--;render.run();});next.setOnClickListener(v->{page[0]++;render.run();});render.run();
        historyDialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);historyDialog.setContentView(col);largeButton(col,"닫기",false,()->historyDialog.dismiss());historyDialog.setOnDismissListener(d->historyDialog=null);historyDialog.show();historyDialog.getWindow().setLayout(-1,-1);
    }
    private void message(String title,String text){ScrollView scroll=new ScrollView(this);TextView content=Ui.text(this,text,20);content.setPadding(Ui.dp(this,24),Ui.dp(this,12),Ui.dp(this,24),Ui.dp(this,12));content.setTextIsSelectable(true);scroll.addView(content);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("확인",null).create();showLargeDialog(dialog);}
    private void showLargeDialog(AlertDialog dialog){dialog.show();for(int which:new int[]{-1,-2,-3}){Button b=dialog.getButton(which);if(b!=null){b.setTextSize(20);b.setMinHeight(Ui.dp(this,56));}}TextView text=dialog.findViewById(android.R.id.message);if(text!=null)text.setTextSize(20);}
    private boolean busy(){if(importing||starting||TransferService.running){Ui.toast(this,"현재 작업이 끝난 뒤 이용해 주세요.");return true;}return false;}
    private void loadFrames(){
        frameList=db.frames();if(db.frame(chosenId)==null)chosenId=frameList.isEmpty()?"":frameList.get(0).id;getPreferences(0).edit().putString("frame",chosenId).apply();
        if(frames!=null){ArrayList<String> names=new ArrayList<>();for(SenderDb.Frame f:frameList)names.add(f.name);if(names.isEmpty())names.add("연결된 액자가 없어요");ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names){
            private View enlarge(View v){TextView t=(TextView)v;t.setTextSize(22);t.setMinHeight(Ui.dp(SenderActivity.this,64));t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(Ui.dp(SenderActivity.this,12),Ui.dp(SenderActivity.this,12),Ui.dp(SenderActivity.this,12),Ui.dp(SenderActivity.this,12));return t;}
            public View getView(int p,View v,ViewGroup parent){return enlarge(super.getView(p,v,parent));}public View getDropDownView(int p,View v,ViewGroup parent){return enlarge(super.getDropDownView(p,v,parent));}
        };frames.setAdapter(a);for(int i=0;i<frameList.size();i++)if(frameList.get(i).id.equals(chosenId)){frames.setSelection(i);break;}}
        refresh();
    }
    private void refresh(){
        if(adapter==null)return;List<SenderDb.Photo> updated=db.pending(chosenId);boolean changed=rows.size()!=updated.size();
        if(!changed)for(int i=0;i<rows.size();i++){SenderDb.Photo a=rows.get(i),b=updated.get(i);if(!a.hash.equals(b.hash)||!a.status.equals(b.status)||!a.error.equals(b.error)||!a.name.equals(b.name)){changed=true;break;}}
        if(changed){rows=updated;HashSet<String> visible=new HashSet<>();for(SenderDb.Photo p:rows)visible.add(p.hash);for(String hash:thumbnails.snapshot().keySet())if(!visible.contains(hash))thumbnails.remove(hash);adapter.notifyDataSetChanged();}
        SenderDb.Frame f=db.frame(chosenId);connection.setText(f==null?"처음 한 번, ⚙ 설정에서 액자를 연결해 주세요.":"받는 액자 · "+f.name);
        boolean enabled=!importing&&!starting&&!TransferService.running;if(frames!=null)frames.setEnabled(enabled);choose.setEnabled(enabled);send.setEnabled(enabled&&!rows.isEmpty()&&f!=null);if(check!=null)check.setEnabled(enabled&&!rows.isEmpty()&&f!=null);if(connectButton!=null)connectButton.setEnabled(enabled);if(clearButton!=null)clearButton.setEnabled(enabled&&!rows.isEmpty());if(settingsConnection!=null)settingsConnection.setText(f==null?"휴대폰과 액자를 같은 와이파이에 연결하세요.":"연결 주소 · "+f.host);
        int done=0,failed=0;for(SenderDb.Photo p:rows){if(p.status.equals("done"))done++;if(p.status.equals("failed"))failed++;}
        send.setText(starting||TransferService.running?"잠시만 기다려 주세요…":failed>0?"다시 보내기":"사진 보내기");
        empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
        empty.setText("보낼 사진이 없어요.\n보낸 사진은 설정의 전송 기록에서 볼 수 있어요.");
        summary.setText(importing?importStatus:(TransferService.message.isEmpty()?(rows.isEmpty()?"사진을 골라 주세요":"고른 사진 "+rows.size()+"장"+(done>0?" · 보낸 사진 "+done+"장":"")+(failed>0?" · 다시 보낼 사진 "+failed+"장":"")):TransferService.message));
    }
    private void pick(){if(busy())return;int available=200-db.photos(null).size();if(available<1){message("사진 목록이 가득 찼어요","설정에서 ‘고른 사진 목록 비우기’를 누르면 새 사진을 고를 수 있어요. 휴대폰 원본과 액자 사진은 그대로 남습니다.");return;}startActivityForResult(new Intent(this,GalleryActivity.class).putExtra(GalleryActivity.LIMIT,available),11);}
    protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=11||result!=RESULT_OK||data==null)return;
        ArrayList<String> selected=data.getStringArrayListExtra(GalleryActivity.RESULT_PHOTOS);if(selected==null||selected.isEmpty())return;
        ArrayList<Uri> uris=new ArrayList<>();for(String value:selected){if(uris.size()>=200)break;Uri uri=Uri.parse(value);if("content".equals(uri.getScheme())&&"media".equals(uri.getAuthority()))uris.add(uri);}if(uris.isEmpty())return;
        importing=true;importStatus="사진을 준비하고 있어요…";refresh();work.execute(()->importPhotos(uris));
    }
    private void importPhotos(List<Uri> uris){int added=0,duplicates=0;ArrayList<String> errors=new ArrayList<>();
        File dir=new File(getFilesDir(),"photos");dir.mkdirs();
        for(Uri uri:uris){File tmp=null;try{
            if(db.photos(null).size()>=200)throw new IOException("선택 목록은 200장까지예요. 전송 후 목록을 비워 주세요.");
            long used=0;for(SenderDb.Photo p:db.photos(null))used+=p.size;
            if(used>500L*1024*1024)throw new IOException("전송 대기 공간이 500MB를 넘었어요. 전송 후 목록을 비워 주세요.");
            String name="사진";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}if(name==null||name.trim().isEmpty())name="사진";if(name.length()>180)name=name.substring(0,180);
            importStatus="사진 준비 중 · "+name;tmp=File.createTempFile("stage-",".part",dir);
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){if(in==null)throw new IOException("사진을 열지 못했어요.");byte[] b=new byte[32768];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>Wire.MAX_PHOTO||total+used>500L*1024*1024)throw new IOException("사진당 50MB, 대기 목록 500MB까지 전송할 수 있어요.");out.write(b,0,n);}}
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(tmp.getPath(),o);
            if(o.outWidth<=0||o.outHeight<=0)throw new IOException("읽을 수 없는 사진이에요: "+name);
            String mime=o.outMimeType;
            if("image/heic".equals(mime)||"image/heif".equals(mime)){
                convertHeif(tmp,o);name=name.replaceFirst("(?i)\\.(heic|heif)$","")+".jpg";
            }else if(!"image/jpeg".equals(mime)&&!"image/png".equals(mime)&&!"image/webp".equals(mime))throw new IOException("JPEG·PNG·WebP·HEIC 사진만 지원해요: "+name);
            String hash;try(InputStream in=new FileInputStream(tmp)){hash=Wire.hash(in);}
            if(db.photoExists(hash)){db.reselect(hash);duplicates++;continue;}
            File dest=new File(dir,hash+".photo");if(dest.exists()&&!dest.delete())throw new IOException("대기 파일을 갱신하지 못했어요.");
            if(!tmp.renameTo(dest))throw new IOException("전송 대기 파일을 저장하지 못했어요.");db.add(hash,name,dest);added++;
        }catch(Exception e){errors.add(Ui.error(e));}finally{if(tmp!=null)tmp.delete();}}
        final int count=added,dups=duplicates;runOnUiThread(()->{importing=false;activeShare=Collections.emptyList();if(isFinishing()||isDestroyed())return;TransferService.message="";refresh();String s=count+"장 추가"+(dups>0?" · 같은 사진 "+dups+"장 제외":"");if(!errors.isEmpty())message(s,"추가하지 못한 사진 "+errors.size()+"장\n"+android.text.TextUtils.join("\n",errors.subList(0,Math.min(5,errors.size()))));else Ui.toast(this,s+" · 받을 액자를 확인하고 ‘사진 보내기’를 눌러 주세요.");drainShares();});
    }
    private void convertHeif(File file,BitmapFactory.Options bounds)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/o.inSampleSize>4096)o.inSampleSize*=2;
        int orientation=new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
        Bitmap image=BitmapFactory.decodeFile(file.getPath(),o);if(image==null)throw new IOException("HEIC 사진을 변환하지 못했어요.");
        Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;}
        Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);
        try(OutputStream out=new FileOutputStream(file)){if(!rotated.compress(Bitmap.CompressFormat.JPEG,92,out))throw new IOException("HEIC 변환 실패");}finally{if(rotated!=image)rotated.recycle();image.recycle();}
    }
    private void startTransfer(boolean onlyCheck){if(busy())return;if(db.frame(chosenId)==null){showSettings();return;}if(db.photos(chosenId).isEmpty()){Ui.toast(this,"사진을 먼저 골라 주세요.");return;}starting=true;TransferService.message="액자 연결 중…";refresh();Intent i=new Intent(this,TransferService.class).putExtra("frame",chosenId).putExtra("checkOnly",onlyCheck);try{startForegroundService(i);}catch(RuntimeException e){starting=false;TransferService.message="전송을 시작하지 못했어요. 다시 눌러 주세요.";refresh();return;}handler.postDelayed(()->{starting=false;refresh();},1000);}
    private void pairDialog(){
        LinearLayout col=Ui.column(this);col.addView(Ui.text(this,"액자에서 ‘액자 사진 받기’를 열어 주세요.",20));
        EditText host=Ui.input(this,col,"액자 주소 · 예: 192.168.0.10:8844",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        SenderDb.Frame current=db.frame(chosenId);if(current!=null)host.setText(current.host);
        host.setTextSize(20);largeButton(col,"같은 와이파이에서 액자 찾기",false,()->discover(host));
        EditText pin=Ui.input(this,col,"처음 연결할 때: 액자의 6자리 연결번호",InputType.TYPE_CLASS_NUMBER);pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        pin.setTextSize(22);ScrollView scroll=new ScrollView(this);scroll.addView(col);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("액자 연결").setView(scroll).setNegativeButton("취소",null).setPositiveButton("연결 확인",null).create();showLargeDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String address=host.getText().toString(),code=pin.getText().toString();if(!code.isEmpty()&&!code.matches("[0-9]{6}")){Ui.toast(this,"연결번호는 6자리예요.");return;}dialog.getButton(-1).setEnabled(false);
            work.execute(()->{try{
                Api.Probe p=Api.probe(address);SenderDb.Frame known=db.frame(p.id);
                if(code.isEmpty()&&known!=null&&Wire.equal(known.fingerprint,p.fingerprint)){
                    known.host=p.host;known.name=p.name;Api.check(known,Collections.emptyList());db.frame(known);chosenId=known.id;getPreferences(0).edit().putString("frame",chosenId).apply();
                    runOnUiThread(()->{dialog.dismiss();loadFrames();Ui.toast(this,"등록된 액자의 주소를 갱신했어요.");});return;
                }
                runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);if(code.isEmpty()){Ui.toast(this,"처음 연결할 때는 액자 화면의 6자리 연결번호가 필요해요.");return;}confirmPair(p,code,dialog);});
            }catch(Exception e){runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);message("액자에 연결하지 못했어요",Ui.error(e)+"\n\n같은 와이파이인지 확인해 주세요. 연결을 해제했던 액자는 새 연결번호를 입력해야 합니다.");});}});
        });
    }
    private void confirmPair(Api.Probe p,String pin,AlertDialog original){
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(p.name+" 연결 확인").setMessage("액자 화면의 ‘연결 확인문구’와 아래 문구가 같은지 확인해 주세요.\n\n"+Wire.shortFingerprint(p.fingerprint)).setNegativeButton("다름 / 취소",null).setPositiveButton("같아요 · 연결",(d,w)->{
            original.getButton(-1).setEnabled(false);work.execute(()->{try{SenderDb.Frame f=Api.pair(p,pin);db.frame(f);chosenId=f.id;getPreferences(0).edit().putString("frame",chosenId).apply();runOnUiThread(()->{original.dismiss();TransferService.message="";loadFrames();Ui.toast(this,"액자를 연결했어요. 이제 사진을 골라 주세요.");});}catch(Exception e){runOnUiThread(()->{original.getButton(-1).setEnabled(true);message("연결 등록 실패",Ui.error(e));});}});
        }).create();showLargeDialog(dialog);
    }
    private void discover(EditText host){
        stopDiscovery();found.clear();resolveQueue.clear();resolving=false;
        nsd=getSystemService(NsdManager.class);
        try{WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);multicast=wifi.createMulticastLock("frame-discovery");multicast.setReferenceCounted(false);multicast.acquire();}catch(Exception ignored){}
        discovery=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}
            public void onServiceFound(NsdServiceInfo info){if(info.getServiceType().contains("_smartframe._tcp"))handler.post(()->{resolveQueue.add(info);resolveNext();});}
            public void onServiceLost(NsdServiceInfo i){}
            public void onDiscoveryStopped(String t){}
            public void onStartDiscoveryFailed(String t,int e){handler.post(()->Ui.toast(SenderActivity.this,"자동 검색을 시작하지 못했어요. 액자 주소를 직접 입력해 주세요."));}
            public void onStopDiscoveryFailed(String t,int e){}
        };
        try{nsd.discoverServices("_smartframe._tcp.",NsdManager.PROTOCOL_DNS_SD,discovery);Ui.toast(this,"액자를 찾고 있어요…");}catch(Exception e){Ui.toast(this,"액자 주소를 직접 입력해 주세요.");}
        handler.postDelayed(()->{stopDiscovery();if(isFinishing()||isDestroyed())return;if(found.isEmpty()){message("검색된 액자가 없어요","액자 화면의 주소를 직접 입력해 주세요. 공유기의 기기 간 통신 차단 설정 때문에 검색이 안 될 수도 있어요.");return;}String[] labels=found.keySet().toArray(new String[0]);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("연결할 액자").setItems(labels,(d,index)->host.setText(found.get(labels[index]))).setNegativeButton("취소",null).create();showLargeDialog(dialog);},6000);
    }
    private void resolveNext(){if(resolving||resolveQueue.isEmpty()||discovery==null)return;resolving=true;NsdServiceInfo item=resolveQueue.remove();
        try{nsd.resolveService(item,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo i,int e){handler.post(()->{resolving=false;resolveNext();});}public void onServiceResolved(NsdServiceInfo i){handler.post(()->{resolving=false;if(i.getHost() instanceof Inet4Address){String address=i.getHost().getHostAddress()+":"+i.getPort();String label=i.getServiceName();byte[] name=i.getAttributes().get("name");if(name!=null)label=new String(name,java.nio.charset.StandardCharsets.UTF_8);found.put(label+" · "+address,address);}resolveNext();});}});}catch(Exception e){resolving=false;resolveNext();}}
    private void stopDiscovery(){if(nsd!=null&&discovery!=null)try{nsd.stopServiceDiscovery(discovery);}catch(Exception ignored){}discovery=null;if(multicast!=null&&multicast.isHeld())multicast.release();}
    private final class PhotosAdapter extends BaseAdapter {
        public int getCount(){return rows.size();}public Object getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View recycled,android.view.ViewGroup parent){
            SenderDb.Photo p=rows.get(position);LinearLayout row=new LinearLayout(SenderActivity.this);row.setPadding(Ui.dp(SenderActivity.this,8),Ui.dp(SenderActivity.this,6),Ui.dp(SenderActivity.this,8),Ui.dp(SenderActivity.this,6));row.setBackgroundColor(Color.WHITE);
            ImageView image=new ImageView(SenderActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(p.name+" 미리보기");row.addView(image,new LinearLayout.LayoutParams(Ui.dp(SenderActivity.this,88),Ui.dp(SenderActivity.this,88)));
            Bitmap cached=thumbnails.get(p.hash);if(cached!=null)image.setImageBitmap(cached);else{image.setImageResource(android.R.drawable.ic_menu_gallery);thumbs.execute(()->{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(p.path,o);o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>160)o.inSampleSize*=2;o.inJustDecodeBounds=false;Bitmap decoded=BitmapFactory.decodeFile(p.path,o);if(decoded!=null){final Bitmap b=orientThumbnail(decoded,p.path);thumbnails.put(p.hash,b);runOnUiThread(()->image.setImageBitmap(b));}});}
            LinearLayout text=new LinearLayout(SenderActivity.this);text.setOrientation(LinearLayout.VERTICAL);text.setPadding(Ui.dp(SenderActivity.this,10),0,0,0);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=Ui.text(SenderActivity.this,p.name,17);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);text.addView(name);
            String label;int color=Ui.INK;
            switch(p.status){case "done":label="✓ 보냈어요";color=Ui.ACCENT;break;case "sending":label="보내는 중 · "+p.error;break;case "failed":label="다시 보내 주세요";color=Color.rgb(165,70,40);break;default:label="보낼 사진";}
            TextView state=Ui.text(SenderActivity.this,label,21);state.setTextColor(color);text.addView(state);
            if(p.status.equals("failed")){TextView reason=Ui.text(SenderActivity.this,p.error,17);reason.setTextColor(color);text.addView(reason);}return row;
        }
    }
    private Bitmap orientThumbnail(Bitmap b,String path){
        try{int orientation=new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();
            switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;default:return b;}
            Bitmap upright=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(upright!=b)b.recycle();return upright;
        }catch(Exception e){return b;}
    }
    protected void onResume(){super.onResume();handler.post(tick);}
    protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    protected void onDestroy(){if(historyDialog!=null)historyDialog.dismiss();if(settings!=null)settings.dismiss();stopDiscovery();handler.removeCallbacksAndMessages(null);work.shutdown();thumbs.shutdown();super.onDestroy();}
}
