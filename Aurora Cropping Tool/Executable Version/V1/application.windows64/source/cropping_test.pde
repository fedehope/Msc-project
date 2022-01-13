/*
This program should take as 'input' a folder of images of auroras 
not yet preprocessed. Then it makes a text file containing the 
dimensions and coordinates of the cropping boxes for the images. 

This project will become a .exe file which then will be used to
make the .txt file in the google drive.
*/

import java.io.File;

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
boolean program_finshed;

// boolean for if the current image has been loaded or not
boolean current_loaded;


    //-- Carrying out the crop analysis --//
// storing the current dimensions of the crop box
int cbox_w;
int cbox_h;
int cbox_x;
int cbox_y;
int initial_size;
boolean evaluating_image;
boolean liveView;
boolean viewing_cutout;
boolean make_cutout;
boolean cutoutSaved;
boolean process_cutout;

// good box dimensions to be considered
ArrayList<Integer> good_cbox_w;
ArrayList<Integer> good_cbox_h;
ArrayList<Integer> good_cbox_x;
ArrayList<Integer> good_cbox_y;
ArrayList<Integer> good_cbox_restarts;

// best box dimensions are saved
int best_cbox_w;
int best_cbox_h;
int best_cbox_x;
int best_cbox_y;

// quality parameters of cbox
int cbox_quality;



void setup()
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

    good_cbox_restarts = new ArrayList<Integer>();
    good_cbox_restarts.add(0);



    // approximately the size of one image
    // (can shrink this later if a scale down is applied also)
    //original_w = 1400;
    //original_h = 1050;
    size(1400,1050);
    background(0);
    frames_per_frame  = 100000;
    evaluating_image  = true;
    liveView          = true;
    program_finshed   = false;
    viewing_cutout    = false;
    cutoutSaved       = false;
    make_cutout       = false;
    process_cutout    = false;
}


/*
 this functions is the heart of the program.
 It evaluates whether or not a specific section 
 of the original image should be included in the 
 crop/cutout.
 */
int evaluate_cbox_quality()
{
    float avg_red   = 0;
    float avg_green = 0;
    float avg_blue  = 0;
    int n           = 0;
    for (int x = cbox_x; x < cbox_x+cbox_w; x++)
    {
        for (int y = cbox_y; y < cbox_y+cbox_h; y++)
        {
            color pixel_color = current_image.pixels[x+current_image.width*y];
            avg_red += red(pixel_color);
            avg_green += green(pixel_color);
            avg_blue += blue(pixel_color);
            n+=1;
        }
    }

    float temp_avg_red   = avg_red;
    float temp_avg_green = avg_green;
    float temp_avg_blue  = avg_blue;
    avg_red   /= n;
    avg_green /= n;
    avg_blue  /= n;
    if ((avg_red+avg_green+avg_blue)/3 < 60 || avg_green < avg_red/2)
    {
        //println(avg_green + " vs " + avg_red);
        //println((avg_red+avg_green+avg_blue)/3);
        return 0;
    }


    good_cbox_w.add(cbox_w);
    good_cbox_h.add(cbox_h);
    good_cbox_x.add(cbox_x);
    good_cbox_y.add(cbox_y);
    return 1;
}
void iterate_cbox(int steps)
{
    cbox_x += ceil(cbox_w*steps*1);
    if (cbox_x + cbox_w >= current_image.width)
    {
        cbox_y += ceil(cbox_h*steps*1);
        cbox_x  = 0;
    }
    if (cbox_y + cbox_h >= current_image.height)
    {
        cbox_x = 0;
        cbox_y = 0;
        cbox_w = floor(cbox_w*0.9);
        cbox_h = floor(cbox_h*0.9);

        if (cbox_w < current_image.width/400)
        {
            evaluating_image = false;
            make_cutout      = true;
        }

        good_cbox_restarts.add(good_cbox_w.size());
    }
}

void draw_good_boxes()
{
    int start = 0;
    int end   = good_cbox_restarts.size()-1;
    if (good_cbox_restarts.size()>=3)
    {
        start = good_cbox_restarts.get(good_cbox_restarts.size()-2);
        end   = good_cbox_restarts.get(good_cbox_restarts.size()-1);
    }
    if (liveView)
    {
        end = good_cbox_h.size();
    }
    for (int i = start; i < end; i++)
    {
        float col_mult = (float(i)/(end-start));
        stroke(0,100,255, 50);
        strokeWeight(2);
        noFill();
        rect(good_cbox_x.get(i),
            good_cbox_y.get(i),
            good_cbox_w.get(i),
            good_cbox_h.get(i));
    }
}

void process_normal_image()
{
    for (int q = 0; q < frames_per_frame; q++)
    {
        if (current_loaded == false)
        {
            // loading the current image to be cropped
            current_image = loadImage( image_dirs[image_index] );
            image(current_image,0,0);

            // load the pixels from that image into the pixel buffer
            current_image.loadPixels();  
            current_loaded = true;  

            int x_size = floor(current_image.width /100);
            int y_size = floor(current_image.height/100);

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

        if (evaluating_image)
        {
            evaluate_cbox_quality();
            iterate_cbox(1);
        }
        else if (make_cutout){
            convert_to_cutout();
            make_cutout    = false;
            viewing_cutout = true;
        }
    }
}

void process_cutout_image()
{
    int x_size = floor(blank_plate.width /300); 
    int y_size = floor(blank_plate.height/300);

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
                color pixel_color = current_image.pixels[x+current_image.width*y];
                avg_red += red(pixel_color);
                avg_green += green(pixel_color);
                avg_blue += blue(pixel_color);
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

void process_next_image()
{
    image_index++;
    current_loaded=false;
    evaluating_image=true;

    if (image_index >= image_dirs.length){
        program_finshed = true;
    }
}

void convert_to_cutout()
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
                color pixel_color = current_image.pixels[x+current_image.width*y];
                blank_plate.pixels[x+current_image.width*y] = pixel_color;
            }
        }
    }
    blank_plate.updatePixels();
}




void draw()
{
    scale(1);
    clear();
    background(0);

    if (viewing_cutout)
    {
        viewing_cutout = false;
        cutoutSaved    = false;
        process_next_image();
    }

    if (program_finshed==false)
    {
        process_normal_image();

        if (viewing_cutout)
        {
            image(blank_plate,0,0);
            if (cutoutSaved==false)
            {
                String filename = image_dirs_no_ext[image_index];
                String newFilename = "";
                for (int c = 0; c < filename.length()-4; c++)
                {
                    newFilename = newFilename+filename.charAt(c);
                }
                filename = newFilename;
                saveFrame(filename+"_cutout"+".jpg");
                cutoutSaved    = true;
                process_cutout = true;
                //viewing_cutout = false;
            }
            
        }else{
            image(current_image,0,0);

            stroke(255,0,0);
            strokeWeight(5);
            noFill();
            rect(cbox_x,cbox_y,cbox_w,cbox_h);

            draw_good_boxes();
        }
    }
    else{
        textSize(50);
        fill(255);
        text("Cutouts and crops created for contents of folder",0,height/2);
    }
}
