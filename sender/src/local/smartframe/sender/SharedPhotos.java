package local.smartframe.sender;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/** Accept read-granted content URIs, never caller-supplied filesystem paths. */
final class SharedPhotos {
    static boolean accepts(Intent intent) {
        return intent != null && (Intent.ACTION_SEND.equals(intent.getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()));
    }
    static ArrayList<Uri> read(Intent intent) {
        if (!accepts(intent)) return new ArrayList<>();
        if (intent.getType() == null || !intent.getType().startsWith("image/"))
            throw new IllegalArgumentException("사진을 선택해서 공유해 주세요.");
        LinkedHashSet<Uri> photos = new LinkedHashSet<>();
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Parcelable> values = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (values != null) for (Parcelable value : values) add(photos, value);
        } else {
            Parcelable value = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (value != null) add(photos, value);
        }
        // Some galleries put streams only in ClipData; others duplicate EXTRA_STREAM here.
        ClipData clip = intent.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) {
            Uri uri = clip.getItemAt(i).getUri();
            if (uri != null) add(photos, uri);
        }
        if (photos.isEmpty()) throw new IllegalArgumentException("공유된 사진을 찾지 못했어요. 갤러리에서 다시 공유해 주세요.");
        return new ArrayList<>(photos);
    }
    private static void add(LinkedHashSet<Uri> photos, Parcelable value) {
        if (!(value instanceof Uri)) throw new IllegalArgumentException("공유된 사진 주소를 읽지 못했어요.");
        Uri uri = (Uri)value;
        if (!"content".equals(uri.getScheme()) || uri.getAuthority() == null || uri.getAuthority().isEmpty())
            throw new IllegalArgumentException("이 갤러리의 공유 방식은 지원하지 않아요. 앱의 ‘사진 고르기’를 이용해 주세요.");
        photos.add(uri);
        if (photos.size() > 200) throw new IllegalArgumentException("한 번에 200장까지 공유해 주세요.");
    }
}
