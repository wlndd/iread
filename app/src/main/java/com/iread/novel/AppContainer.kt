package com.iread.novel

import android.content.Context
import androidx.room.Room
import com.iread.novel.core.parser.TxtBookParser
import com.iread.novel.data.db.IReadDatabase
import com.iread.novel.data.files.PrivateBookFileStore
import com.iread.novel.data.repository.RoomBookRepository
import com.iread.novel.domain.*
import java.io.File

class AppContainer(val database: IReadDatabase, privateBookRoot: File) {
    constructor(context: Context) : this(
        Room.databaseBuilder(context.applicationContext, IReadDatabase::class.java, "iread.db").build(),
        context.filesDir,
    )

    val repository = RoomBookRepository(database.bookDao())
    val files = PrivateBookFileStore(privateBookRoot)
    val importTxtBook = ImportTxtBookUseCase(repository, files, TxtBookParser(), TimeSource(System::currentTimeMillis))
    val deleteBook = DeleteBookUseCase(repository, files)
}
