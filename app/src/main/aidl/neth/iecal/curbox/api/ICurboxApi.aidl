package neth.iecal.curbox.api;
import android.os.Bundle;
interface ICurboxApi {
    int apiVersion();
    boolean isGranted();
    String execute(String command, in Bundle args);
    String query(String state);
    String list(String kind);
}
