package android.net;
public final class Uri implements android.os.Parcelable {
    private final java.net.URI value;
    private Uri(String text){value=java.net.URI.create(text);}
    public static Uri parse(String text){return new Uri(text);}
    public String getScheme(){return value.getScheme();}
    public String getAuthority(){return value.getAuthority();}
    public boolean equals(Object other){return other instanceof Uri && value.equals(((Uri)other).value);}
    public int hashCode(){return value.hashCode();}
}
