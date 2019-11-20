package ie.boboco.imap

import com.sun.mail.imap.IMAPFolder


object IMAPFilerUndo extends IMAPStore {

  store doWith { _ =>
    for (folderToProcess <- foldersToProcess) {   // we've supplied a list of folders like "2016"
      val name = folderToProcess.getFullName
      log(s"Processing $name")

      // depending on whether we are in top-level mode, either we have peers like "2016", "2016.1", "2016.2", OR,
      // we have 2016.1, 2016.2, etc. as children of 2016. This kind of twisted logic come up with the right
      // sequence of childFolders without caring too. Much. This could be more robust, or we could simplify
      // things by picking a lane regarding top-level or not top-level.  The problem is that the experiments with
      // DEVONthink are kind of expensive, so there is a lot of "thinking between runs". I don't yet know if whatever
      // report issues with number of messages is also a problem if those messages are spread across several sub-folders,
      // or whether they have to be completely flat at the top. I'm starting with trying to test completely flat across
      // the top, and if that works, then I will try to see if I can nest them, since that's a much less cluttered
      // user experience in the mail client itself.
      safeOpenParent(folderToProcess) doWith { parentFolder =>
        open(folderToProcess) doWith { folderToProcess =>
          for (childFolder <- (if (topLevel) parentFolder else folderToProcess).list(s"$name.*").map(_.asInstanceOf[IMAPFolder])) {
            open(childFolder) doWith { childFolder =>

              // Basically we copy all the messages in 2016.1 to 2016, and then delete 2016.1, no matter where it is.
              val messagesToMove = childFolder.getMessages
              if (!dryRun) {
                log(s"Copying ${messagesToMove.size} messages from " +
                  s"${childFolder.getFullName} to ${folderToProcess.getFullName}")
                childFolder.copyMessages(messagesToMove, folderToProcess)
              } else {
                log(s"Would move ${messagesToMove.size} messages from " +
                  s"${childFolder.getFullName} to ${folderToProcess.getFullName}")
              }
            }
            if (!dryRun) {
              log(s"Deleting ${childFolder.getFullName}")
              assert(childFolder.delete(true), s"Could not delete ${childFolder.getFullName}")
            } else {
              log(s"Would delete ${childFolder.getFullName}")
            }
          }
        }
      }
    }
  }
}