
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.ceil;

public class main_parallel {

    static int resize_scale = 8;
    static String file_name = "dark";

    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = dimg.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return dimg;
    }

    public static int find_closest_int(int n, int m){

        if(n%m != 0){
            return find_closest_int(n,m-1);
        } else {
            return m;
        }
    }

    public static void main(String[] args){

        //Load the image
        System.out.println("Loading image..");
        BufferedImage img = null;
        try {
            img = ImageIO.read(new File("image/" + file_name + ".jpg"));
            //img = resize(img, img.getWidth()*2, img.getHeight()*2);
        }catch (IOException e) {
            System.out.println(e);
            return;
        }
        try{
            //Display the image
            ImageIcon icon = new ImageIcon(resize(img, img.getWidth()/resize_scale, img.getHeight()/resize_scale));
            JLabel label = new JLabel(icon, JLabel.CENTER);
            JOptionPane.showMessageDialog(null, label, "Image not Equalized", -1);

            System.out.println("Start processing");
            long startTime = System.currentTimeMillis();

            int total_thread = Runtime.getRuntime().availableProcessors();
            System.out.println("Thread available = " + total_thread);

            int[] histogram = new int[256];
            int[] histogram_equalized = new int[256];

            //First pool: convert the image from RGB to YCbCr, with a ThreadPool with fixed size of total_thread.
            //Some callables are created, each converting and computing the histogram of a part of the image.
            //Take the result of each callable through Futures, and add together all the local histograms

            ExecutorService es = Executors.newFixedThreadPool(total_thread);
            ArrayList<Future> futures = new ArrayList<>();

            int starting_point = (int)ceil((double)img.getHeight() / (double)total_thread);
            int sub_height = (int)ceil((double)img.getHeight() / (double)total_thread);

            for (int i = 0; i < total_thread; i++) {

                if(i == total_thread-1 && img.getHeight()%total_thread != 0){
                    sub_height = img.getHeight() - i*(int)ceil((double)img.getHeight() / (double)total_thread);
                }

                futures.add(es.submit(new RGB_to_YCbCr_thread(
                        img.getSubimage(
                                0,
                                (i*starting_point),
                                (img.getWidth()),
                                (sub_height))
                )));

            }

            //Wait for the pool to finish
            for(Future<int[]> future : futures){
                int[] local_histogram = future.get();
                for(int j = 0; j < 256; j++) {
                    histogram[j] += local_histogram[j];
                }
            }

            long time_1 = System.currentTimeMillis();
            System.out.println("First pool ended in : " + (time_1 - startTime) + " msec");

            //Compute the cdf of the histogram
            int sum = 0;
            int[] cdf = new int[256];

            for(int i = 0; i < histogram.length; i++){

                sum += histogram[i];
                cdf[i] = sum;
            }

            //Compute the equalized histogram dividing the histogram in total_thread part, each one
            //processed by a single thread
            int histogram_threads;

            //Find the closest number to total_thread that is multiple of 256
            histogram_threads = find_closest_int(256, total_thread);

            futures.clear();

            for(int i=0;i<histogram_threads;i++){
                futures.add(es.submit(new Equalize_Thread(i, histogram_threads, histogram, cdf, histogram_equalized, img.getWidth(), img.getHeight())));
            }

            for(Future future : futures){
                future.get();
            }

            long time_2 = System.currentTimeMillis();
            System.out.println("Second pool ended in : " + (time_2 - time_1) + " msec");

            //Third pool: map the new values of the Y channel and convert the image from YCbCr to RGB
            sub_height = (int)ceil((double)img.getHeight() / (double)total_thread);
            futures.clear();

            for (int i = 0; i < total_thread; i++){

                if(i == total_thread-1 && img.getHeight()%total_thread != 0){
                    sub_height = img.getHeight() - i*(int)ceil((double)img.getHeight() / (double)total_thread);
                }

                futures.add(es.submit(new YCbCr_to_RGB_thread(
                        img.getSubimage(
                                0,
                                (i*starting_point),
                                (img.getWidth()),
                                (sub_height)),
                        histogram_equalized)));
            }

            es.shutdown();
            es.awaitTermination(1, TimeUnit.MINUTES);

            long endTime   = System.currentTimeMillis();
            System.out.println("Third pool ended in: " + (endTime - time_2) + " msec");
            System.out.println("Total time in : " + (endTime - startTime) + " msec");

            System.out.println("Showing the equalized image..");
            //Display the new image
            icon = new ImageIcon(resize(img, img.getWidth()/resize_scale, img.getHeight()/resize_scale));
            label = new JLabel(icon, JLabel.CENTER);
            JOptionPane.showMessageDialog(null, label, "Image Equalized", -1);

            System.out.println("Saving the image..");
            //Save output image
            //File output_file = new File("image/" + file_name + "_equalized_thread.jpg");
            //ImageIO.write(img, "jpg", output_file);
            //System.out.println("Image saved!");
        } catch (Exception e){
            System.out.println(e);
            return;
        }
    }
}
