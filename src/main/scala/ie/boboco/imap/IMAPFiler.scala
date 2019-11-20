package ie.boboco.imap

import com.sun.mail.imap.{IMAPFolder, SortTerm}
import javax.mail.{Flags, Folder}

import scala.util.Try

object IMAPFiler extends IMAPStore {

  store doWith { _ =>
    for (folderToFile <- foldersToProcess) {
      safeOpenParent(folderToFile) doWith { parentFolder =>     // folder where, e.g., 2016.1 will sit
        println(parentFolder.getFullName)
        val name = folderToFile.getFullName
        val msgCount = folderToFile.getMessageCount
        log(s"Processing $name ($msgCount messages)")
        open(folderToFile) doWith { folder =>                 // e.g., 2016 (folder with lots of messages)

          // get a sorted list of all messages (sort is efficiently done server side, typically)
          val messages = folder.getSortedMessages(Array(SortTerm.ARRIVAL))

          // compute how many folders we are going to need
          val folderCount = (msgCount / threshold) + (if ((msgCount % threshold) == 0) 0 else 1)
          log(s"$name: $folderCount")

          // for each folder we are going to create
          for (i <- 1 to folderCount) {

            // Create the folder, where it lives, and return both its name (for logging) and the folder instance
            val (newName, destFolder) = incrementalFolder(name, if (topLevel) parentFolder else folder)

            if (!dryRun) {
              // create it so we can modify it, and fail if that didn't work
              val isCreated = destFolder.create(Folder.HOLDS_MESSAGES | Folder.READ_WRITE)
              assert(isCreated, logMsg(s"Could not create $newName in ${folder.getFullName}"))
            }

            // We batch through the messages in order
            val toMove = messages.slice((i - 1) * threshold, i * threshold)

            if (!dryRun) {
              // copy this batch of messages to the new folder
              log(s"Copying ${toMove.length} messages to $newName")
              folder.copyMessages(toMove, destFolder)

              // delete these messages (copyMessages should throw if there's a problem, so this is safe,
              // ALTHOUGH we could do further checks here TODO
              // Note this is an efficient, batch, server-side delete in most implementations
              log(s"Deleting ${toMove.length} messages from $name")
              folder.setFlags(toMove, new Flags(Flags.Flag.DELETED), true)

              if (topLevel) {
                // If we created new peer folders to, e.g., like 2016.1, etc, then 2016 is empty now and
                // we can delete. We fail if in fact it's not empty
                assert(folder.getMessageCount == 0, s"Folder should be empty: ${folder.getFullName}")
                folder.delete(false)
              }
            } else {
              log(s"Would be copying ${toMove.length} messages to $newName")
            }
          }
        }
      }
    }
  }

  def incrementalFolder(name: String, folder: IMAPFolder): (String, IMAPFolder) = {

    // For, e.g., 2016, we check for 2016.1, 2016.2, etc. until we find one that doesn't
    // exist, and create a reference to that folder. Note that the folder doesn't actually
    // exist, yet, until we call `create` on it.
    var i = 1
    // as soon as we find a non-existent folder name "$name.$j", we're ready to go
    while (folder.getFolder(s"$name.$i").exists()) i += 1
    (s"$name.$i", folder.getFolder(s"$name.$i").asInstanceOf[IMAPFolder])
  }
}
