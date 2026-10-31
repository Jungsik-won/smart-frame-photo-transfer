package android.content;
public final class ClipData {
    private final java.util.List<Item> items=new java.util.ArrayList<>();
    public ClipData(android.net.Uri... uris){for(android.net.Uri uri:uris)items.add(new Item(uri));}
    public int getItemCount(){return items.size();}
    public Item getItemAt(int i){return items.get(i);}
    public static final class Item {
        private final android.net.Uri uri;
        Item(android.net.Uri uri){this.uri=uri;}
        public android.net.Uri getUri(){return uri;}
    }
}
