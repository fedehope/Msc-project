import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import java.io.File; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class cropping_tool_v2 extends PApplet {


/*
This program should take as 'input' a folder of images of auroras 
not yet preprocessed. Then it makes a text file containing the 
dimensions and coordinates of the cropping boxes for the images. 

This project will become a .exe file which then will be used to
make the .txt file in the google drive.
*/



int frames_per_frame;

    //-- Loading, storing and locating images --//
// index of the current image being crop tested
int image_index;

// string of the directory of the list of images 
// to be cropped
String image_list_dir;

// folder object for the folder containing the 
// image files to be cropped
java.io.File image_folder;

// array containing all the locations of the images
String[] image_dirs;

// array containing all the locations of the images (but without the initial dir part)
String[] image_dirs_no_ext;

// PImage object for the current image to be cropped
PImage current_image;

// blank plate to put the cutout aurora onto
PImage blank_plate;

// blank image to be turned into the crop of the current image
PImage crop_plate;

// boolean to indicate the program has completed all cropping and cuting
boolean program_finshed;

// boolean for if the current image has been loaded or not
boolean current_loaded;


    //-- Carrying out the crop analysis --//
// storing the current dimensions of the crop box
int cbox_w;
int cbox_h;
int cbox_x;
int cbox_y;

// variable for storing the inital size to start the search with
int initial_size;

// storing the minimum size the cboxes are allowed to shrink to 
float min_cbox_size;

// storing the size of the boxes used to cutout
float min_cutout_box_width;

// start/stop running boxes over the image
boolean evaluating_image;

// good box dimensions to be considered
ArrayList<Integer> good_cbox_w;
ArrayList<Integer> good_cbox_h;
ArrayList<Integer> good_cbox_x;
ArrayList<Integer> good_cbox_y;
ArrayList<Integer> good_cbox_restarts;

// good box dimensions to be considered (for CUTOUT)
ArrayList<Integer> cut_good_cbox_w;
ArrayList<Integer> cut_good_cbox_h;
ArrayList<Integer> cut_good_cbox_x;
ArrayList<Integer> cut_good_cbox_y;
ArrayList<Integer> cut_good_cbox_restarts;

// best box dimensions are saved
int best_cbox_w;
int best_cbox_h;
int best_cbox_x;
int best_cbox_y;
float score;
float best_score;

// quality parameters of cbox
int cbox_quality;



public void setup()
{
    /* 
    Setting the initial parameters, like the given name of 
    the folder that should contain the images and the index
    (set to 0) of the current image.
    */
    image_list_dir    = "/uncropped_images";
    image_folder      = new java.io.File(dataPath(image_list_dir));
    image_dirs        = image_folder.list();
    image_dirs_no_ext = image_dirs;
    image_index       = 0;

    // reformatting the dirs to contain the master folder
    for (int i = 0; i < image_dirs.length; i++)
    {image_dirs[i] = image_list_dir + '/' + image_dirs[i];}

    
    // initalization of the storage of 'good' boxes
    good_cbox_w = new ArrayList<Integer>();
    good_cbox_h = new ArrayList<Integer>();
    good_cbox_x = new ArrayList<Integer>();
    good_cbox_y = new ArrayList<Integer>();
    cut_good_cbox_w = new ArrayList<Integer>();
    cut_good_cbox_h = new ArrayList<Integer>();
    cut_good_cbox_x = new ArrayList<Integer>();
    cut_good_cbox_y = new ArrayList<Integer>();
    score       = 0;
    best_score  = 0;

    good_cbox_restarts = new ArrayList<Integer>();
    good_cbox_restarts.add(0);

    cut_good_cbox_restarts = new ArrayList<Integer>();
    cut_good_cbox_restarts.add(0);

    min_cbox_size        = loadImage(image_dirs[0]).width/5;
    min_cutout_box_width = loadImage(image_dirs[0]).width/30;



    // approximately the size of one image
    // (can shrink this later if a scale down is applied also)
    //original_w = 1400;
    //original_h = 1050;
    
    background(0);
    frames_per_frame  = 10;
    evaluating_image  = true;
    program_finshed   = false;
}




/*
 this functions is the heart of the program.
 It evaluates whether or not a specific section 
 of the original image should be included in the 
 crop/cutout.
 */
public int evaluate_cbox_quality(boolean is_cutout)
{
    float score = 0;

    float avg_red   = 0;
    float avg_green = 0;
    float avg_blue  = 0;
    int n           = 0;

    boolean has_black_pixel = false;
    for (int x = cbox_x; x < cbox_x+cbox_w; x++)
    {
        for (int y = cbox_y; y < cbox_y+cbox_h; y++)
        {
            int pixel_color = current_image.pixels[x+current_image.width*y];
            avg_red += red(pixel_color);
            avg_green += green(pixel_color);
            avg_blue += blue(pixel_color);

            if (red(pixel_color) < 40 && green(pixel_color) < 40 && blue(pixel_color) < 40)
            {has_black_pixel = true;} 

            n+=1;
        }
    }

    float temp_avg_red   = avg_red;
    float temp_avg_green = avg_green;
    float temp_avg_blue  = avg_blue;
    avg_red   /= n;
    avg_green /= n;
    avg_blue  /= n;
    if ((avg_red+avg_green+avg_blue)/3 < 50 || avg_green < avg_red/2 || has_black_pixel)
    {
        //println(avg_green + " vs " + avg_red);
        //println((avg_red+avg_green+avg_blue)/3);
        return 0;
    }

    if (is_cutout)
    {
        cut_good_cbox_w.add(cbox_w);
        cut_good_cbox_h.add(cbox_h);
        cut_good_cbox_x.add(cbox_x);
        cut_good_cbox_y.add(cbox_y);
    }else{
        good_cbox_w.add(cbox_w);
        good_cbox_h.add(cbox_h);
        good_cbox_x.add(cbox_x);
        good_cbox_y.add(cbox_y);

        score = (   (((avg_red+avg_green+avg_blue)/3)/150)/10  
                +   (avg_green / 255)/10    
                +   4*((float)cbox_w / (float)initial_size)/5);

        if (score > best_score)
        {
            best_cbox_w = cbox_w;
            best_cbox_h = cbox_h;
            best_cbox_x = cbox_x;
            best_cbox_y = cbox_y;

            best_score = score;
        }
    }
    return 1;
}
public int evaluate_cbox_quality()
{
    float score = 0;

    float avg_red   = 0;
    float avg_green = 0;
    float avg_blue  = 0;
    int n           = 0;

    boolean has_black_pixel = false;
    for (int x = cbox_x; x < cbox_x+cbox_w; x++)
    {
        for (int y = cbox_y; y < cbox_y+cbox_h; y++)
        {
            int pixel_color = current_image.pixels[x+current_image.width*y];
            avg_red += red(pixel_color);
            avg_green += green(pixel_color);
            avg_blue += blue(pixel_color);

            if (red(pixel_color) < 40 && green(pixel_color) < 40 && blue(pixel_color) < 40)
            {has_black_pixel = true;} 

            n+=1;
        }
    }

    float temp_avg_red   = avg_red;
    float temp_avg_green = avg_green;
    float temp_avg_blue  = avg_blue;
    avg_red   /= n;
    avg_green /= n;
    avg_blue  /= n;
    if ((avg_red+avg_green+avg_blue)/3 < 50 || avg_green < avg_red/2 || has_black_pixel)
    {
        //println(avg_green + " vs " + avg_red);
        //println((avg_red+avg_green+avg_blue)/3);
        return 0;
    }

    good_cbox_w.add(cbox_w);
    good_cbox_h.add(cbox_h);
    good_cbox_x.add(cbox_x);
    good_cbox_y.add(cbox_y);

    score = (   (((avg_red+avg_green+avg_blue)/3)/150)/10  
            +   (avg_green / 255)/10    
            +   4*((float)cbox_w / (float)initial_size)/5);

    if (score > best_score)
    {
        best_cbox_w = cbox_w;
        best_cbox_h = cbox_h;
        best_cbox_x = cbox_x;
        best_cbox_y = cbox_y;

        best_score = score;
    }
    return 1;
}




/*
Iterate the cbox by moving it along or changing its size
*/
public void iterate_cbox(int steps, boolean is_cutout)
{
    cbox_x += ceil(cbox_w*0.2f*steps*1);
    if (cbox_x + cbox_w >= current_image.width)
    {
        cbox_y += ceil(cbox_h*0.2f*steps*1);
        cbox_x  = 0;
    }
    if (cbox_y + cbox_h >= current_image.height)
    {
        cbox_x = 0;
        cbox_y = 0;
        cbox_w = floor(cbox_w*0.9f);
        cbox_h = floor(cbox_h*0.9f);

        if (is_cutout)
        {
            if (cbox_w < min_cutout_box_width)
            {
                evaluating_image = false;
            }
        }else
        {
            if (cbox_w < min_cbox_size)
            {
                evaluating_image = false;
            }
        }

        if (is_cutout) {cut_good_cbox_restarts.add(cut_good_cbox_w.size());}
        else           {    good_cbox_restarts.add(    good_cbox_w.size());}
        
    }
}
public void iterate_cbox(int steps)
{
    cbox_x += ceil(cbox_w*0.2f*steps*1);
    if (cbox_x + cbox_w >= current_image.width)
    {
        cbox_y += ceil(cbox_h*0.2f*steps*1);
        cbox_x  = 0;
    }
    if (cbox_y + cbox_h >= current_image.height)
    {
        cbox_x = 0;
        cbox_y = 0;
        cbox_w = floor(cbox_w*0.9f);
        cbox_h = floor(cbox_h*0.9f);

        if (cbox_w < min_cbox_size)
        {
            evaluating_image = false;
        }
        good_cbox_restarts.add(good_cbox_w.size());
    }
}



public void draw_good_boxes()
{
    int start = 0;
    int end   = 0;
    if (good_cbox_restarts.size()>=3)
    {
        start = good_cbox_restarts.get(good_cbox_restarts.size()-2);
        end   = good_cbox_restarts.get(good_cbox_restarts.size()-1);
    }

    start = 0;
    end   = good_cbox_h.size()-1;

    for (int i = start; i < end; i++)
    {
        float col_mult = (PApplet.parseFloat(i)/(end-start));
        stroke(0,100,255, 50);
        strokeWeight(2);
        noFill();
        rect(good_cbox_x.get(i),
            good_cbox_y.get(i),
            good_cbox_w.get(i),
            good_cbox_h.get(i));
    }
    stroke(255,250,200);
    strokeWeight(4);
    rect(best_cbox_x,best_cbox_y,best_cbox_w,best_cbox_h);
}



public void process_normal_image(boolean is_cutout)
{

    // Loop the process of evaluating and moving the box until the image 
    // has been covered
    while(evaluating_image)
    {
        evaluate_cbox_quality(is_cutout);
        iterate_cbox(1,is_cutout);
    }
}
public void process_normal_image()
{

    // boolean evaluating_image = true;
    // Loop the process of evaluating and moving the box until the image 
    // has been covered
    while(evaluating_image)
    {
        evaluate_cbox_quality();
        iterate_cbox(1);
    }
}



/*
generate cutout image and crop image, save both
*/
public void cutout_and_crop_image()
{
    // proces the normal image but 'true' for is_cutout now
    // load the pixels from that image into the pixel buffer
    evaluating_image = true;
    process_normal_image(true);

    // creating the cutout image
    blank_plate = createImage(current_image.width,current_image.height,RGB);
    blank_plate.loadPixels();

    int start = 0;
    int end   = good_cbox_h.size();
    if (cut_good_cbox_restarts.size()>=3)
    {
        start = cut_good_cbox_restarts.get(cut_good_cbox_restarts.size()-2);
        end   = cut_good_cbox_restarts.get(cut_good_cbox_restarts.size()-1);
    }
    for (int i = start; i < end; i++)
    {
        int g_cbox_x = cut_good_cbox_x.get(i);
        int g_cbox_y = cut_good_cbox_y.get(i);
        int g_cbox_w = cut_good_cbox_w.get(i);
        int g_cbox_h = cut_good_cbox_h.get(i);
        for (int x = g_cbox_x; x < g_cbox_x+g_cbox_w; x++)
        {
            for (int y = g_cbox_y; y < g_cbox_y+g_cbox_h; y++)
            {
                int pixel_color = current_image.pixels[x+current_image.width*y];
                blank_plate.pixels[x+current_image.width*y] = pixel_color;
            }
        }
    }
    blank_plate.updatePixels();



    // now creating the cropped image (not cutout)
    crop_plate = createImage(best_cbox_w,best_cbox_h,RGB);
    crop_plate.loadPixels();

    for (int x = best_cbox_x; x < best_cbox_x+best_cbox_w; x++)
    {
        for (int y = best_cbox_y; y < best_cbox_y+best_cbox_h; y++)
        {
            int pixel_color = current_image.pixels[x+current_image.width*y];
            crop_plate.pixels[(x-best_cbox_x)+best_cbox_w*(y-best_cbox_y)] = pixel_color;
        }
    }

    crop_plate.updatePixels();


    String filename = image_dirs_no_ext[image_index];
    String newFilename = "";
    for (int c = 0; c < filename.length()-4; c++)
    {
        newFilename = newFilename+filename.charAt(c);
    }
    String finalFilename = "";
    for (int c = 17; c < newFilename.length()-1; c++)
    {
        finalFilename = finalFilename+newFilename.charAt(c);
    }
    filename = finalFilename;
    blank_plate.save("cutouts/"+filename+"_cutout"+".jpg");

    image(crop_plate,0,0);
    crop_plate.save("optimal_crops/"+filename+"_optcrop"+".jpg");
}




/*
process the cutout of the image to potentially make it better 
quality (not currently working due to logic of program)
*/
public void process_cutout_image()
{
    int x_size = floor(blank_plate.width /50); 
    int y_size = floor(blank_plate.height/50);

    initial_size = x_size;
    if (y_size < x_size)
    {
        initial_size = y_size;
    }

    cbox_w = initial_size;
    cbox_h = initial_size;
    cbox_x = 0;
    cbox_y = 0;

    blank_plate.loadPixels();

    boolean notFullyScanned = true;
    while (notFullyScanned)
    {
        float avg_red   = 0;
        float avg_green = 0;
        float avg_blue  = 0;
        int n           = 0;
        for (int x = cbox_x; x < cbox_x+cbox_w; x++)
        {
            for (int y = cbox_y; y < cbox_y+cbox_h; y++)
            {
                int pixel_color = current_image.pixels[x+current_image.width*y];
                avg_red   += red(pixel_color);
                avg_green += green(pixel_color);
                avg_blue  += blue(pixel_color);
                n+=1;
            }
        }

        float temp_avg_red   = avg_red;
        float temp_avg_green = avg_green;
        float temp_avg_blue  = avg_blue;
        avg_red   /= n;
        avg_green /= n;
        avg_blue  /= n;

        if ((avg_red+avg_green+avg_blue)/3 < 100)
        {
            for (int x = cbox_x; x < cbox_x+cbox_w; x++)
            {
                for (int y = cbox_y; y < cbox_y+cbox_h; y++)
                {
                    blank_plate.pixels[x+y*cbox_w] = color(0,0,0);
                }
            }
        }
        blank_plate.updatePixels();

        cbox_x += ceil(cbox_w*1);
        if (cbox_x + cbox_w >= blank_plate.width)
        {
            cbox_y += ceil(cbox_h*1);
            cbox_x  = 0;
        }
        if (cbox_y + cbox_h >= blank_plate.height)
        {
            notFullyScanned = false;
            println("done");
        }
    }
}


/*
switch to the next image and reset the logic of the program 
(end if needed)
*/
public void process_next_image()
{
    image_index++;
    current_loaded=false;
    evaluating_image=true;

    if (image_index >= image_dirs.length){
        program_finshed = true;
    }
}


/*
create a cutout of the current image by looking for pixels in all of
the clipping boxes and swapping the pixel colour over from the real image to the blank image
*/
public void convert_to_cutout()
{
    blank_plate = createImage(current_image.width,current_image.height,RGB);
    blank_plate.loadPixels();

    int start = 0;
    int end   = good_cbox_h.size();
    if (good_cbox_restarts.size()>=3)
    {
        start = good_cbox_restarts.get(good_cbox_restarts.size()-2);
        end   = good_cbox_restarts.get(good_cbox_restarts.size()-1);
    }
    for (int i = start; i < end; i++)
    {
        int g_cbox_x = good_cbox_x.get(i);
        int g_cbox_y = good_cbox_y.get(i);
        int g_cbox_w = good_cbox_w.get(i);
        int g_cbox_h = good_cbox_h.get(i);
        for (int x = g_cbox_x; x < g_cbox_x+g_cbox_w; x++)
        {
            for (int y = g_cbox_y; y < g_cbox_y+g_cbox_h; y++)
            {
                int pixel_color = current_image.pixels[x+current_image.width*y];
                blank_plate.pixels[x+current_image.width*y] = pixel_color;
            }
        }
    }
    blank_plate.updatePixels();
}










public void load_current_image()
{
    // loading the current image to be cropped
    current_image = loadImage( image_dirs[image_index] );
    image(current_image,0,0);

    // load the pixels from that image into the pixel buffer
    current_image.loadPixels();  
    current_loaded = true;  

    int x_size = floor(current_image.width /1);
    int y_size = floor(current_image.height/1);

    initial_size = x_size;
    if (y_size < x_size)
    {
        initial_size = y_size;
    }

    cbox_w = initial_size;
    cbox_h = initial_size;
    cbox_x = 0;
    cbox_y = 0;
}
public void run_kernals_over_image()
{
    process_normal_image();
}
public void display_image_and_good_boxes()
{
    draw_good_boxes();
}



/*
visual part of the .exe, not necessary in final version 
(only good for bugfixing)
*/
public void draw()
{
    scale(0.5f);
    clear();
    background(0);

    // Program should have a way to stop doing stuff
    if (program_finshed==false)
    {
        // 1-> Load image 
        // 2-> Evaluate kernels 
        // 3-> display the best crop box and the mesh of the aurora
        // 4-> create the cropped and cutout versions
        // 5-> Save the cropped and cutout versions
        // 6-> skip to the next image

        // 1->
        load_current_image();
        // 2->
        run_kernals_over_image();
        // 3->
        display_image_and_good_boxes();
        // 4->
        cutout_and_crop_image();
        // 5-> 
        image(blank_plate,0,0);
        image(crop_plate,0,0);
        //Making it draw the boxes over the top
        display_image_and_good_boxes();

        //Save files correctly (naming)
        String filename = image_dirs_no_ext[image_index];
        String newFilename = "";
        for (int c = 0; c < filename.length()-4; c++)
        {
            newFilename = newFilename+filename.charAt(c);
        }
        String finalFilename = "";
        for (int c = 17; c < newFilename.length()-1; c++)
        {
            finalFilename = finalFilename+newFilename.charAt(c);
        }
        filename = finalFilename;
        saveFrame("debug/"+filename);
        
        // 6-> 
        if (image_index < image_dirs.length-1)
        {
            //Skip to next image
            image_index++;
            current_loaded = false;
            evaluating_image = true;
        }else{
            program_finshed = true;
        }
        
    }
    else{
        textSize(50);
        fill(255);
        text("Cutouts and crops created for contents of folder",0,height/2);
    }
}
  public void settings() {  size(700,525); }
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "cropping_tool_v2" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
