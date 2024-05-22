
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp



data class User(val username: String, val passwordHash: ByteArray, val securityQuestion: String, val securityAnswer: String)

fun startFileServer(port: Int, fileToShare: File) {
    val serverSocket = ServerSocket(port)
    println("Server started on port $port")
    val clientSocket = serverSocket.accept()
    println("Client connected: ${clientSocket.inetAddress}")

    BufferedInputStream(FileInputStream(fileToShare)).use { fileInput ->
        BufferedOutputStream(clientSocket.getOutputStream()).use { output ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
    }

    clientSocket.close()
    serverSocket.close()
    println("File sent successfully!")
}

fun connectToFileServer(serverAddress: String, port: Int, saveToFile: File) {
    val socket = Socket(serverAddress, port)
    println("Connected to server: $serverAddress")

    BufferedInputStream(socket.getInputStream()).use { input ->
        BufferedOutputStream(FileOutputStream(saveToFile)).use { fileOutput ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                fileOutput.write(buffer, 0, bytesRead)
            }
        }
    }

    socket.close()
    println("File received successfully!")
}

@Composable
fun App() {
    var password by remember { mutableStateOf("") }
    var user by remember { mutableStateOf<User?>(null) }
    var message by remember { mutableStateOf("") }
    var showSnackbar by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var filesList by remember { mutableStateOf(listOf<File>()) }
    var securityAnswer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var showPasswordReset by remember { mutableStateOf(false) }
    var showUploadButton by remember { mutableStateOf(true) }
    var signingUp by remember { mutableStateOf(false) }
    var updatingProfile by remember { mutableStateOf(false) }
    var managingFiles by remember { mutableStateOf(false) }
    var sharingFile by remember { mutableStateOf(false) }
    var receivingFile by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        val userData = loadUser()
        if (userData != null) {
            user = userData
            filesList = getEncryptedFiles()
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (user == null && !signingUp) {
                    SignUpPrompt {
                        signingUp = true
                    }
                } else {
                    when {
                        user == null && signingUp -> {
                            SignUpScreen(onSignUp = { newUser ->
                                user = newUser
                                filesList = getEncryptedFiles()
                                signingUp = false
                            })
                        }
                        updatingProfile -> {
                            ProfileUpdateScreen(user = user!!, onProfileUpdated = { updatedUser ->
                                user = updatedUser
                                updatingProfile = false
                                message = "Profile updated successfully!"
                                showSnackbar = true
                            })
                        }
                        managingFiles -> {
                            FileManagementScreen(files = filesList, onFilesUpdated = {
                                filesList = getEncryptedFiles()
                                managingFiles = false
                            })
                        }
                        sharingFile -> {
                            SharingScreen(filesList, onShare = { file, port ->
                                startFileServer(port, file)
                                sharingFile = false
                            }, onCancel = { sharingFile = false })
                        }
                        receivingFile -> {
                            ReceivingScreen(onReceive = { serverAddress, port, saveToFile ->
                                connectToFileServer(serverAddress, port, saveToFile)
                                receivingFile = false
                            }, onCancel = { receivingFile = false })
                        }
                        else -> {
                            LoginScreen(
                                password = password,
                                showPassword = showPassword,
                                onPasswordChange = { password = it },
                                onShowPasswordChange = { showPassword = it },
                                onLoginClick = {
                                    if (checkPassword(password, user?.passwordHash ?: byteArrayOf())) {
                                        message = "Login successful!"
                                        showSnackbar = true
                                        filesList = getEncryptedFiles()
                                        failedAttempts = 0
                                        showPasswordReset = false
                                        showUploadButton = true
                                    } else {
                                        failedAttempts++
                                        if (failedAttempts >= 3) {
                                            showPasswordReset = true
                                        }
                                        message = "Incorrect password. Please try again."
                                        showSnackbar = true
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (showPasswordReset) {
                                PasswordResetScreen(
                                    securityAnswer = securityAnswer,
                                    newPassword = newPassword,
                                    securityQuestion = user!!.securityQuestion,
                                    showPassword = showPassword,
                                    onSecurityAnswerChange = { securityAnswer = it },
                                    onNewPasswordChange = { newPassword = it },
                                    onShowPasswordChange = { showPassword = it },
                                    onPasswordResetClick = {
                                        if (securityAnswer == user!!.securityAnswer && newPassword.isNotEmpty()) {
                                            user = user!!.copy(passwordHash = hashPassword(newPassword))
                                            saveUser(user!!)
                                            message = "Password reset successful!"
                                            showSnackbar = true
                                            failedAttempts = 0
                                            showPasswordReset = false
                                        } else {
                                            message = "Incorrect security answer or empty password. Please try again."
                                            showSnackbar = true
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(message)
                            Spacer(modifier = Modifier.height(16.dp))
                            user?.let {
                                FilesListScreen(
                                    filesList = filesList,
                                    password = password,
                                    onFileDecrypt = { file ->
                                        val decryptedFile = decryptFile(password, file)
                                        message = decryptedFile?.let { 
                                            "File decrypted successfully: ${it.absolutePath}" 
                                        } ?: "Failed to decrypt file. Check your password."
                                        showSnackbar = true
                                    },
                                    onFileDelete = { file ->
                                        val confirmed = confirmDelete(file)
                                        if (confirmed) {
                                            val deleted = deleteFile(file)
                                            message = if (deleted) {
                                                filesList = filesList.filter { it != file }
                                                "File deleted successfully"
                                            } else {
                                                "Failed to delete file"
                                            }
                                            showSnackbar = true
                                        }
                                    },
                                    onFileRename = { file, newName ->
                                        if (renameFile(file, newName)) {
                                            message = "File renamed successfully"
                                            filesList = getEncryptedFiles()
                                        } else {
                                            message = "Failed to rename file"
                                        }
                                        showSnackbar = true
                                    },
                                    onFileMove = { file, newDir ->
                                        val dir = File(newDir)
                                        if (moveFile(file, dir)) {
                                            message = "File moved successfully"
                                            filesList = getEncryptedFiles()
                                        } else {
                                            message = "Failed to move file"
                                        }
                                        showSnackbar = true
                                    }
                                )
                            }
                            if (showUploadButton) {
                                Button(onClick = {
                                    val fileChooser = JFileChooser()
                                    fileChooser.isMultiSelectionEnabled = true
                                    val result = fileChooser.showOpenDialog(null)
                                    if (result == JFileChooser.APPROVE_OPTION) {
                                        val selectedFiles = fileChooser.selectedFiles
                                        selectedFiles.forEach { selectedFile ->
                                            saveEncryptedFile(password, selectedFile.absolutePath)
                                        }
                                        filesList = getEncryptedFiles()
                                    }
                                }) {
                                    Text("Upload Files")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (filesList.isNotEmpty()) {
                                BatchDecryptButton(files = filesList, password = password) { decryptedFiles ->
                                    message = "Files decrypted successfully: ${decryptedFiles.filterNotNull().joinToString { it.absolutePath }}"
                                    showSnackbar = true
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { updatingProfile = true }) {
                                Text("Update Profile")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { managingFiles = true }) {
                                Text("Manage Files")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { sharingFile = true }) {
                                Text("Share File")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { receivingFile = true }) {
                                Text("Receive File")
                            }
                        }
                    }
                }
                if (showSnackbar) {
                    Snackbar(
                        action = {
                            Button(onClick = { showSnackbar = false }) {
                                Text("OK")
                            }
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(message)
                    }
                }
            }
        }
    }
}



@Composable
fun SharingScreen(files: List<File>, onShare: (File, Int) -> Unit, onCancel: () -> Unit) {
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var port by remember { mutableStateOf("5000") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Select a file to share")
        files.forEach { file ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedFile == file,
                    onClick = { selectedFile = file }
                )
                Text(file.name)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = { selectedFile?.let { onShare(it, port.toInt()) } }) {
                Text("Share")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun ReceivingScreen(
    onReceive: (String, Int, File) -> Unit,
    onCancel: () -> Unit
) {
    var serverAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5000") }
    var saveToFile by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter Server Details",
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ServerInputFields(
            serverAddress = serverAddress,
            port = port,
            onServerAddressChange = { serverAddress = it },
            onPortChange = { port = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SaveLocationButton(onSaveLocationSelected = { file -> saveToFile = file })
        Spacer(modifier = Modifier.height(16.dp))
        ActionButtons(
            onSave = { saveToFile?.let { onReceive(serverAddress, port.toInt(), it) } },
            onCancel = onCancel
        )
    }
}

@Composable
private fun ServerInputFields(
    serverAddress: String,
    port: String,
    onServerAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = serverAddress,
            onValueChange = { onServerAddressChange(it) },
            label = { Text("Server Address") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { onPortChange(it) },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SaveLocationButton(onSaveLocationSelected: (File) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Select Save Location")
    }

    if (showDialog) {
        SaveLocationDialog(
            onDialogDismiss = { showDialog = false },
            onSaveLocationSelected = onSaveLocationSelected
        )
    }
}

@Composable
private fun SaveLocationDialog(
    onDialogDismiss: () -> Unit,
    onSaveLocationSelected: (File) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDialogDismiss() },
        title = { Text("Save File As") },
        confirmButton = {
            Button(
                onClick = {
                    // Simulated file selection
                    onSaveLocationSelected(File("path/to/save/file"))
                    onDialogDismiss()
                }
            ) {
                Text("Simulated File Chooser")
            }
        },
        dismissButton = {
            Button(onClick = { onDialogDismiss() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ActionButtons(onSave: () -> Unit, onCancel: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(onClick = onCancel) {
            Text("Cancel")
        }
        Button(onClick = onSave) {
            Text("Receive")
        }
    }
}

@Composable
fun FileManagementScreen(files: List<File>, onFilesUpdated: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var newDir by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize()
    ) {
        Text(
            "File Management",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        files.forEach { file ->
            FileRow(file = file) {
                selectedFile = file
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        selectedFile?.let { file ->
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton(
                    text = "Rename",
                    onClick = {
                        if (renameFile(file, newName)) {
                            onFilesUpdated()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newDir,
                    onValueChange = { newDir = it },
                    label = { Text("New Directory") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton(
                    text = "Move",
                    onClick = {
                        val dir = File(newDir)
                        if (moveFile(file, dir)) {
                            onFilesUpdated()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FileRow(file: File, onItemClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(file.name)
        ActionButton(
            text = "Manage",
            onClick = onItemClick
        )
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(100.dp)
    ) {
        Text(text)
    }
}


@Composable
fun ProfileUpdateScreen(user: User, onProfileUpdated: (User) -> Unit) {
    var username by remember { mutableStateOf(user.username) }
    var newPassword by remember { mutableStateOf("") }
    var securityQuestion by remember { mutableStateOf(user.securityQuestion) }
    var securityAnswer by remember { mutableStateOf(user.securityAnswer) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Update Profile", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = securityQuestion,
            onValueChange = { securityQuestion = it },
            label = { Text("Security Question") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = securityAnswer,
            onValueChange = { securityAnswer = it },
            label = { Text("Security Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (username.isNotEmpty() && (newPassword.isNotEmpty() || user.passwordHash.isNotEmpty()) && securityQuestion.isNotEmpty() && securityAnswer.isNotEmpty()) {
                val updatedUser = user.copy(
                    username = username,
                    passwordHash = if (newPassword.isNotEmpty()) hashPassword(newPassword) else user.passwordHash,
                    securityQuestion = securityQuestion,
                    securityAnswer = securityAnswer
                )
                saveUser(updatedUser)
                onProfileUpdated(updatedUser)
                message = "Profile updated successfully!"
            } else {
                message = "Please fill out all fields."
            }
        }) {
            Text("Update Profile")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(message)
    }
}

/*
@Composable
fun BatchUploadButton(password: String, onFilesUploaded: () -> Unit) {
    Button(onClick = {
        val fileChooser = JFileChooser().apply {
            isMultiSelectionEnabled = true
        }
        val result = fileChooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFiles = fileChooser.selectedFiles.map { it.absolutePath }
            batchSaveEncryptedFiles(password, selectedFiles)
            onFilesUploaded()
        }
    }) {
        Text("Upload Files")
    }
}
*/
@Composable
fun BatchDecryptButton(files: List<File>, password: String, onFilesDecrypted: (List<File?>) -> Unit) {
    Button(onClick = {
        val decryptedFiles = batchDecryptFiles(password, files)
        onFilesDecrypted(decryptedFiles)
    }) {
        Text("Decrypt Files")
    }
}


@Composable
fun SignUpPrompt(onSignUpClick: () -> Unit) {
    Button(onClick = onSignUpClick) {
        Text("Sign Up")
    }
}

@Composable
fun LoginScreen(
    password: String,
    showPassword: Boolean,
    onPasswordChange: (String) -> Unit,
    onShowPasswordChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Login",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = showPassword,
                onCheckedChange = onShowPasswordChange
            )
            Text(
                text = "Show Password",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLoginClick,
            modifier = Modifier.widthIn(max = 200.dp)
        ) {
            Text("Login")
        }
    }
}
@Composable
fun PasswordResetScreen(
    securityAnswer: String,
    newPassword: String,
    securityQuestion: String,
    showPassword: Boolean,
    onSecurityAnswerChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onShowPasswordChange: (Boolean) -> Unit,
    onPasswordResetClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Password Reset",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            value = securityAnswer,
            onValueChange = onSecurityAnswerChange,
            label = { Text(securityQuestion) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = { Text("New Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = showPassword,
                onCheckedChange = onShowPasswordChange
            )
            Text(
                text = "Show Password",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onPasswordResetClick,
            modifier = Modifier.widthIn(max = 200.dp)
        ) {
            Text("Reset Password")
        }
    }
}

@Composable
fun FilesListScreen(
    filesList: List<File>,
    password: String,
    onFileDecrypt: (File) -> Unit,
    onFileDelete: (File) -> Unit,
    onFileRename: (File, String) -> Unit,
    onFileMove: (File, String) -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text("Stored Files:", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))
        filesList.forEach { file ->
            FileItem(
                file = file,
                password = password,
                onFileDecrypt = onFileDecrypt,
                onFileDelete = onFileDelete,
                onFileRename = onFileRename,
                onFileMove = onFileMove
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun FileItem(
    file: File,
    password: String,
    onFileDecrypt: (File) -> Unit,
    onFileDelete: (File) -> Unit,
    onFileRename: (File, String) -> Unit,
    onFileMove: (File, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = file.name,
                fontSize = 18.sp,
                color = Color.Black
            )
            Row {
                Button(
                    onClick = { onFileDecrypt(file) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Blue),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Unlock & Download", color = Color.White)
                }
                Button(
                    onClick = { onFileDelete(file) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            }
        }
    }
}
@Composable
fun SignUpScreen(onSignUp: (User) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sign Up", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = securityQuestion,
            onValueChange = { securityQuestion = it },
            label = { Text("Security Question") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = securityAnswer,
            onValueChange = { securityAnswer = it },
            label = { Text("Security Answer") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (username.isNotEmpty() && password.isNotEmpty() && securityQuestion.isNotEmpty() && securityAnswer.isNotEmpty()) {
                    val user = User(username, hashPassword(password), securityQuestion, securityAnswer)
                    saveUser(user)
                    onSignUp(user)
                }
            },
            modifier = Modifier.widthIn(max = 200.dp)
        ) {
            Text("Sign Up", color = Color.White)
        }
    }
}


fun renameFile(file: File, newName: String): Boolean {
    val newFile = File(file.parent, newName)
    return file.renameTo(newFile)
}

fun moveFile(file: File, newDir: File): Boolean {
    if (!newDir.exists()) {
        newDir.mkdirs()
    }
    val newFile = File(newDir, file.name)
    return file.renameTo(newFile)
}


fun batchSaveEncryptedFiles(password: String, filePaths: List<String>) {
    filePaths.forEach { filePath ->
        saveEncryptedFile(password, filePath)
    }
}



fun batchDecryptFiles(password: String, files: List<File>): List<File?> {
    return files.map { decryptFile(password, it) }
}


fun confirmDelete(file: File): Boolean {
    val dialogResult = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete ${file.name}?", "Confirm Delete", JOptionPane.YES_NO_OPTION)
    return dialogResult == JOptionPane.YES_OPTION
}

fun deleteFile(file: File): Boolean {
    return try {
        file.delete()
    } catch (e: Exception) {
        false
    }
}



fun loadUser(): User? {
    val userFile = File("user.dat")
    return if (userFile.exists()) {
        val (username, encodedPasswordHash, securityQuestion, securityAnswer) = userFile.readText().split(":")
        val passwordHash = hexStringToByteArray(encodedPasswordHash)
        User(username, passwordHash, securityQuestion, securityAnswer)
    } else {
        null
    }
}

private fun hexStringToByteArray(hexString: String): ByteArray {
    val byteArray = ByteArray(hexString.length / 2)
    for (i in 0 until hexString.length step 2) {
        val byte = hexString.substring(i, i + 2).toInt(16).toByte()
        byteArray[i / 2] = byte
    }
    return byteArray
}

fun saveUser(user: User) {
    val userFile = File("user.dat")
    val encodedPasswordHash = user.passwordHash.joinToString("") { "%02x".format(it) }
    userFile.writeText("${user.username}:${encodedPasswordHash}:${user.securityQuestion}:${user.securityAnswer}")
}

fun hashPassword(password: String): ByteArray {
    val sha = MessageDigest.getInstance("SHA-256")
    return sha.digest(password.toByteArray())
}

fun checkPassword(password: String, expectedHash: ByteArray): Boolean {
    val actualHash = hashPassword(password)
    return MessageDigest.isEqual(expectedHash, actualHash)
}

fun getStorageDirectory(): File {
    val storageDir = File(System.getProperty("user.home"), "MySecureFiles")
    if (!storageDir.exists()) {
        storageDir.mkdirs()
    }
    return storageDir
}

fun saveEncryptedFile(password: String, filePath: String) {
    val key = generateKey(password)
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, key)

    val fileBytes = File(filePath).readBytes()
    val encryptedBytes = cipher.doFinal(fileBytes)

    val storageDir = getStorageDirectory()
    val fileName = File(filePath).name
    val encryptedFilePath = File(storageDir, "$fileName.enc")
    encryptedFilePath.writeBytes(encryptedBytes)
}

fun getEncryptedFiles(): List<File> {
    val storageDir = getStorageDirectory()
    return storageDir.listFiles { _, name -> name.endsWith(".enc") }?.toList() ?: emptyList()
}

fun decryptFile(password: String, encryptedFile: File): File? {
    return try {
        val key = generateKey(password)
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)

        val encryptedBytes = encryptedFile.readBytes()
        val decryptedBytes = cipher.doFinal(encryptedBytes)

        val decryptedFilePath = File(encryptedFile.parentFile, encryptedFile.name.removeSuffix(".enc"))
        decryptedFilePath.writeBytes(decryptedBytes)
        decryptedFilePath
    } catch (e: Exception) {
        null
    }
}

fun generateKey(password: String): SecretKey {
    val key = password.toByteArray(Charsets.UTF_8)
    val sha = MessageDigest.getInstance("SHA-256")
    val keyBytes = sha.digest(key).copyOf(16)
    return SecretKeySpec(keyBytes, "AES")
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
