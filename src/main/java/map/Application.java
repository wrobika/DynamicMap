package map;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.serializer.KryoSerializer;
import org.datasyslab.geospark.serde.GeoSparkKryoRegistrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import osrm.OsrmController;

import static java.lang.Math.cos;

@SpringBootApplication
public class Application {

    public static JavaSparkContext sc;

    public static void main(String[] args) throws IOException {

        SpringApplication.run(Application.class, args);

        SparkConf conf = new SparkConf().setAppName("DynamicMap")
            .setMaster("local[*]")
            .set("spark.serializer", KryoSerializer.class.getName())
            .set("spark.kryo.registrator", GeoSparkKryoRegistrator.class.getName());
        sc = new JavaSparkContext(conf);


        //TODO:zrobic pisanie przez geosparka
        //TODO:spytac jak obliczyc 250m?
//        BufferedWriter writer = new BufferedWriter(new FileWriter("grid.csv"));
//
//        //https://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
//        for(Double lat = 49.964071; lat<50.133260; lat+= 0.00224578){
//            Double lat_radian = lat * 3.14/180;
//            for(Double lon = 19.786568; lon < 20.228223; lon+=0.00224579/cos(lat_radian)) {
//                writer.write(lat.toString() + "," + lon.toString() +",19\n");
//            }
//        }
//        writer.close();

        Point2D.Double startPoint = new Point2D.Double(19.960292,50.021329);
        OsrmController.downloadRoutesFromPoint(startPoint);
    }
}
