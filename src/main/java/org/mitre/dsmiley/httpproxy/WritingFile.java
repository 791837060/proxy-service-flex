package org.mitre.dsmiley.httpproxy;

import java.io.File;

import java.io.FileOutputStream;

import java.io.IOException;

import java.io.OutputStreamWriter;

import java.io.PrintWriter;

public class WritingFile {

/**

* write something to a file,it will create a file if the file is not exist.

*

* @param path

* file path

* @param content

* the content you want to write to the file

* @param isappend

* true,will append the file.false,will rewrite the file

* @throws IOException

* exception

*/

public static void appendFile(String path, String content, boolean isappend)

throws IOException {

File f = new File(path);

if (!f.exists()) {

f.createNewFile();

}

PrintWriter pw = new PrintWriter(new FileOutputStream(f, isappend));

pw.print(content);

pw.flush();

pw.close();

}

/**

* create a file , and assgin the file's encode

*

* @param path

* file path

* @param content

* the content you want to write to the file

* @param isappend

* true,will append the file.false,will rewrite the file

* @param encode

* file's encode

* @throws IOException

* exception

*/

public static void appendFile(String path, String content,

boolean isappend, String encode) throws IOException {

File f = new File(path);

if (!f.exists()) {

f.createNewFile();

}

OutputStreamWriter isw = new OutputStreamWriter(new FileOutputStream(f,

isappend), encode);

isw.write(content);

isw.flush();

isw.close();

}

/**

* @param args

*/

public static void main(String[] args) {

try {

WritingFile.appendFile("/x.txt", "what is your name", false);

} catch (IOException e) {

// TODO Auto-generated catch block

e.printStackTrace();

}

try {

WritingFile.appendFile("/x2.txt", "what is your name", false,"UTF-8");

} catch (IOException e) {

// TODO Auto-generated catch block

e.printStackTrace();

}

}

}