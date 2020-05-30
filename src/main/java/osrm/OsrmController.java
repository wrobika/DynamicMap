package osrm;

import java.io.IOException;

public class OsrmController {
    private static final String scriptUpdateOSRM = "/home/ubuntu/updateAllOSRM.sh";
    private static final String scriptStartOSRM = "/home/ubuntu/startAllOSRM.sh";
    private static final String scriptPingOSRM = "/home/ubuntu/pingOSRM.sh";

    public static void startOSRM() throws Exception {
        Runtime.getRuntime().exec(scriptStartOSRM);
        pingOSRM();
    }

    static void updateOSRM() throws Exception {
        Runtime.getRuntime().exec(scriptUpdateOSRM);
        Thread.sleep(30000);
        pingOSRM();
    }

    private static void pingOSRM() throws Exception {
        Process process = Runtime.getRuntime().exec(scriptPingOSRM);
        while (process.waitFor() != 0) {
            System.out.println("I am waiting for OSRM response");
            Thread.sleep(1000);
            process = Runtime.getRuntime().exec(scriptPingOSRM);
        }
    }
}
