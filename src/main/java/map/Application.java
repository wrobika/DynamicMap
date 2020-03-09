package map;

import com.vividsolutions.jts.geom.Point;
import org.apache.hadoop.fs.FileSystem;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.serializer.KryoSerializer;
import org.datasyslab.geospark.serde.GeoSparkKryoRegistrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class Application {

    public static JavaSparkContext sc;
    public static List<Point> ambulances;
    public static FileSystem hdfs;

    public static void main(String[] args) throws IOException {

        SpringApplication.run(Application.class, args);

        SparkConf conf = new SparkConf().setAppName("DynamicMap")
            .setMaster("spark://master:7077")
            .set("spark.serializer", KryoSerializer.class.getName())
            .set("spark.kryo.registrator", GeoSparkKryoRegistrator.class.getName())
            .set("spark.hadoop.validateOutputSpecs", "false");

        sc = new JavaSparkContext(conf);
        ambulances = new ArrayList<>();
        hdfs = FileSystem.get(sc.hadoopConfiguration());

        //createGrid();
    }
}