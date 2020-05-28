package map;

import com.vividsolutions.jts.geom.Point;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.serializer.KryoSerializer;
import org.datasyslab.geospark.serde.GeoSparkKryoRegistrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static osrm.DownloadController.manageOSRM;
import static osrm.DownloadController.startOSRM;

@SpringBootApplication
public class Application {

    public static JavaSparkContext sc;
    public static List<Point> ambulances;
    public static FileSystem hdfs;

    public static void main(String[] args) throws Exception
    {
        SpringApplication.run(Application.class, args);

        SparkConf conf = new SparkConf().setAppName("DynamicMap")
            .setMaster("spark://master:7077")
	    //.setMaster("local[*]")
            .set("spark.serializer", KryoSerializer.class.getName())
            .set("spark.kryo.registrator", GeoSparkKryoRegistrator.class.getName())
            .set("spark.hadoop.validateOutputSpecs", "false");
	    //.set("spark.driver.host", "master");
        
        sc = new JavaSparkContext(conf);
        ambulances = new ArrayList<>();
        hdfs = FileSystem.get(sc.hadoopConfiguration());

        //createGrid();
        copyRequiredFiles();
        startOSRM();
    }

    private static void copyRequiredFiles()
    {
        String[] fileNames = new String[] {
            GridController.regularGridFile,
            GridController.irregularGridFile,
            GridController.boundaryKrakowLocation,
        };
        try
        {
            for(String file : fileNames)
            {
                FileSystem hdfs = Application.hdfs;
                Path path = new Path(file);
                if(!hdfs.exists(path))
                {
                    Configuration conf = new Configuration();
                    FileSystem localFS = FileSystem.getLocal(conf);
                    Path localPath = new Path(localFS.getHomeDirectory().toString() + file);
                    hdfs.copyFromLocalFile(localPath, path);
                }
            }
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
        }
    }
}
