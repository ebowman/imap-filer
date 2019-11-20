# IMAP organizer

I use [DEVONthink](https://www.devontechnologies.com/apps/devonthink), 
a pretty amazing personal database, to organize my life.

One feature of DT3 is that it  can backup your email and include it for 
search with all the other data in your life. Unfortunately, this 
implementation is pretty flakey.

I've found it crashes when importing my email from Apple Mail pretty often. 
The advice I got from the support team was to try to break up, for examples, 
my archived email into several folders. This is a huge pain, so I wrote 
some code to do it for me.

What this program does:

Locates IMAP folders with more than a certain threshold (configurable) 
number of emails, and creates subfolders of that folder to distribute 
the emails into.

So suppose you have a folder 2014 with 22,000 emails in it. If you run 
this program with a threshold 4000, it will create 6 folders, 
2014/2014.1, 2014/2014.2, etc. and move emails from 2014 into the 
individual subfolders, sorted ascending by date, with no more than 
the specified threshold number of emails in each folder.

This code is more or less unsupported, very specific for this exact purpose,
but maybe somebody, somewhere, finds it useful as they run into the kind
of IMAP task that they'd like to easily automate.

\- Eric Bowman
 