package local.smartframe.receiver;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import local.smartframe.transfer.Ui;
import local.smartframe.transfer.core.Wire;

public final class ReceiverActivity extends Activity {
    private final Handler handler=new Handler();private TextView status,address,pin,trust,total,title,connection,storageStatus;
    private final Runnable update=new Runnable(){public void run(){refresh();handler.postDelayed(this,2000);}};
    public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(Ui.ACCENT);
        ScrollView scroll=new ScrollView(this);LinearLayout col=Ui.column(this);scroll.addView(col);setContentView(scroll);
        title=Ui.text(this,"액자 사진 받기",30);col.addView(title);
        status=Ui.text(this,"수신 준비 중…",20);col.addView(status);
        connection=Ui.text(this,"",15);col.addView(connection);
        col.addView(Ui.text(this,"1. 휴대폰과 같은 와이파이에 연결하세요",17));address=Ui.text(this,"",24);address.setTextIsSelectable(true);col.addView(address);
        col.addView(Ui.text(this,"2. 휴대폰 앱에서 액자를 선택하고 연결번호를 입력하세요",17));pin=Ui.text(this,"",32);col.addView(pin);
        trust=Ui.text(this,"",18);col.addView(trust);total=Ui.text(this,"",18);col.addView(total);
        storageStatus=Ui.text(this,"저장소 확인 중…",17);col.addView(storageStatus);
        Ui.button(this,col,"사진 저장소 선택 (내부 / USB / SD)",()->chooseStorage());
        Ui.button(this,col,"새 연결번호 만들기 (10분 유효)",()->{ReceiverService s=ReceiverService.current;if(s!=null&&s.identity!=null){s.identity.renewPin();refresh();}});
        Ui.button(this,col,"연결 진단 보기",()->{
            TextView details=Ui.text(this,ConnectionDiagnostics.read(this),16);details.setTextIsSelectable(true);ScrollView view=new ScrollView(this);view.addView(details);
            new AlertDialog.Builder(this).setTitle("연결 진단 · 0.1.5").setView(view).setPositiveButton("닫기",null).setNeutralButton("복사",(d,w)->{android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("액자 연결 진단",ConnectionDiagnostics.read(this)));Ui.toast(this,"연결 진단을 복사했어요.");}).show();
        });
        Ui.button(this,col,"Fotoo 열기",()->openFotoo());
        Ui.button(this,col,"액자 이름 바꾸기",()->rename());
        Ui.button(this,col,"배터리 제한 설정 열기",()->{try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){Ui.toast(this,"설정에서 이 앱의 배터리 제한을 해제해 주세요.");}});
        Ui.button(this,col,"등록된 휴대폰 연결 해제",()->new AlertDialog.Builder(this).setTitle("휴대폰 연결을 모두 해제할까요?").setMessage("사진은 유지됩니다. 휴대폰에서 다시 연결해야 해요.").setNegativeButton("취소",null).setPositiveButton("연결 해제",(d,w)->{ReceiverService s=ReceiverService.current;if(s!=null&&s.identity!=null){s.identity.revoke();refresh();}}).show());
        col.addView(Ui.text(this,"선택한 저장소의 Pictures/SmartFrame에 사진을 저장합니다. 폴더가 없으면 첫 사진을 받을 때 자동 생성합니다.\n저장소를 바꾸면 이후 전송부터 적용되고 기존 사진은 원래 위치에 남습니다.\n첫 사진을 받은 뒤 Fotoo → Gallery에서 해당 저장소의 SmartFrame 앨범을 선택하세요.\n이 앱은 화면을 닫아도 사진을 받으며, 재부팅 후 자동으로 시작합니다.\n수신 앱 버전 0.1.5",16));
        ReceiverService.start(this);
    }
    private void chooseStorage(){
        final StorageTargets storage=new StorageTargets(this);
        try{
            final java.util.List<StorageTargets.Target> choices=storage.available();
            if(choices.isEmpty()){Ui.message(this,"사용 가능한 저장소가 없어요","USB·SD카드를 연결하고 프레임 설정에서 저장소가 인식되는지 확인해 주세요.");return;}
            String[] labels=new String[choices.size()];int checked=-1;
            for(int i=0;i<choices.size();i++){labels[i]=storage.describe(choices.get(i));if(choices.get(i).volume.equals(storage.selected()))checked=i;}
            final int[] selection={checked};
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("새 사진을 저장할 곳")
                .setSingleChoiceItems(labels,checked,(d,index)->selection[0]=index)
                .setNegativeButton("취소",null).setPositiveButton("선택",null)
                .setNeutralButton("저장소가 안 보여요",(d,w)->Ui.message(this,"저장소 연결 확인","USB·SD카드를 연결한 뒤 다시 열어 주세요. 안드로이드에서 쓰기 가능한 사진 저장소로 인식된 장치가 표시됩니다.\n\n선택한 저장소에 첫 사진을 받으면 Pictures/SmartFrame 폴더가 자동 생성됩니다. 기존 사진은 이동하지 않습니다.")).create();
            dialog.show();dialog.getButton(-1).setOnClickListener(v->{
                if(selection[0]<0){Ui.toast(this,"저장소를 선택해 주세요.");return;}
                try{storage.select(choices.get(selection[0]));dialog.dismiss();refresh();Ui.message(this,"사진 저장소를 변경했어요","새로 시작하는 전송부터 적용됩니다. 기존 사진은 원래 위치에 남습니다.\n\n휴대폰에서 ‘액자에 있는 사진 확인’을 누르면 선택한 저장소를 기준으로 표시가 갱신됩니다.");}
                catch(Exception e){Ui.message(this,"저장소를 선택하지 못했어요",e.getMessage());}
            });
        }catch(Exception e){Ui.message(this,"저장소 확인 실패","저장소 연결 상태를 확인한 뒤 다시 시도해 주세요.");}
    }
    private void openFotoo(){
        final String pkg="com.bo.fotoo";
        android.content.pm.PackageManager pm=getPackageManager();
        StringBuilder diagnostic=new StringBuilder();
        // A missing launcher query result is not proof that the app is uninstalled.
        // This exported entry point is verified in the supplied Fotoo 2.3.40 APK.
        for(int route=0;route<3;route++){
            try{
                Intent intent;
                if(route==0)intent=pm.getLaunchIntentForPackage(pkg);
                else if(route==1)intent=Intent.makeMainActivity(new ComponentName(pkg,"com.bo.fotoo.ui.home.FTHomeActivity"));
                else intent=pm.getLeanbackLaunchIntentForPackage(pkg);
                if(intent==null){diagnostic.append(route+1).append(": 실행 아이콘 조회 결과 없음\n");continue;}
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);return;
            }catch(ActivityNotFoundException|SecurityException e){
                diagnostic.append(route+1).append(": ").append(e.getClass().getSimpleName()).append('\n');
            }
        }
        String installed;
        try{
            android.content.pm.ApplicationInfo app=pm.getApplicationInfo(pkg,0);
            installed=app.enabled?"Fotoo 설치는 확인했지만 실행 화면을 열지 못했어요.":"Fotoo가 사용 중지 상태예요. 앱 설정에서 사용으로 바꿔 주세요.";
        }catch(android.content.pm.PackageManager.NameNotFoundException e){
            installed="이 수신 앱에서 Fotoo 설치 정보를 확인하지 못했어요. 앱 목록에서 Fotoo가 직접 열리면 재설치할 필요는 없어요.";
        }
        new AlertDialog.Builder(this).setTitle("Fotoo 실행 확인").setMessage(installed+"\n\n프레임 앱 목록에서 Fotoo를 직접 열고 Gallery → SmartFrame을 선택할 수 있어요.\n\n"+diagnostic)
            .setPositiveButton("닫기",null).setNeutralButton("앱 설정",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+pkg)));}
                catch(ActivityNotFoundException|SecurityException e){Ui.toast(this,"프레임 설정의 앱 목록에서 Fotoo를 확인해 주세요.");}
            }).show();
    }
    private void rename(){ReceiverService s=ReceiverService.current;if(s==null||s.identity==null)return;EditText e=new EditText(this);e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_TEXT);e.setText(s.identity.name());new AlertDialog.Builder(this).setTitle("액자 이름").setView(e).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{s.identity.name(e.getText().toString());refresh();}).show();}
    private void refresh(){status.setText(ReceiverService.state);connection.setText("자체 검사: "+ReceiverService.selfCheck+"\n"+ReceiverService.lastConnection);address.setText(ReceiverService.addresses());storageStatus.setText("사진 저장소\n"+new StorageTargets(this).summary());ReceiverService s=ReceiverService.current;if(s!=null&&s.identity!=null){title.setText(s.identity.name());pin.setText(s.identity.pin());trust.setText("연결 확인문구: "+Wire.shortFingerprint(s.identity.fingerprint)+"\n휴대폰에 표시된 문구와 같아야 해요.");if(s.photos!=null)total.setText("전체 저장소 수신 기록 "+s.photos.count()+"장 · 연결번호는 연결 성공 시 바뀝니다");}}
    protected void onResume(){super.onResume();handler.post(update);}
    protected void onPause(){handler.removeCallbacks(update);super.onPause();}
}
