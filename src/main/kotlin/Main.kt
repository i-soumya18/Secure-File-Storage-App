import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.SecretKeySpec
import javax.swing.JFileChooser
import javax.swing.JOptionPane

data class User(val username: String, val passwordHash: ByteArray, val securityQuestion: String, val securityAnswer: String)

@Composable
@Preview
fun App() {
    var password by remember { mutableStateOf("") }
    var user by remember { mutableStateOf<User?>(null) }
    var message by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var filesList by remember { mutableStateOf(listOf<File>()) }
    var securityAnswer by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var showPasswordReset by remember { mutableStateOf(false) }
    var showUploadButton by remember { mutableStateOf(true) }
    var signingUp by remember { mutableStateOf(false) }

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
                    if (user == null && signingUp) {
                        SignUpScreen(onSignUp = { newUser ->
                            user = newUser
                            filesList = getEncryptedFiles()
                            signingUp = false
                        })
                    } else {
                        LoginScreen(
                            password = password,
                            showPassword = showPassword,
                            onPasswordChange = { password = it },
                            onShowPasswordChange = { showPassword = it },
                            onLoginClick = {
                                if (checkPassword(password, user?.passwordHash ?: byteArrayOf())) {
                                    message = "Login successful!"
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
                                        failedAttempts = 0
                                        showPasswordReset = false
                                    } else {
                                        message = "Incorrect security answer or empty password. Please try again."
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(message)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (user != null) {
                        FilesListScreen(
                            filesList = filesList,
                            password = password,
                            onFileDecrypt = { file ->
                                val decryptedFile = decryptFile(password, file)
                                if (decryptedFile != null) {
                                    message = "File decrypted successfully: ${decryptedFile.absolutePath}"
                                } else {
                                    message = "Failed to decrypt file. Check your password."
                                }
                            },
                            onFileDelete = { file ->
                                val confirmed = confirmDelete(file)
                                if (confirmed) {
                                    val deleted = deleteFile(file)
                                    if (deleted) {
                                        message = "File deleted successfully"
                                        filesList = filesList.filter { it != file }
                                    } else {
                                        message = "Failed to delete file"
                                    }
                                }
                            }
                        )
                    }
                    if (showUploadButton) {
                        Button(onClick = {
                            val fileChooser = JFileChooser()
                            val result = fileChooser.showOpenDialog(null)
                            if (result == JFileChooser.APPROVE_OPTION) {
                                val selectedFile = fileChooser.selectedFile
                                saveEncryptedFile(password, selectedFile.absolutePath)
                                filesList = getEncryptedFiles()
                            }
                        }) {
                            Text("Upload File")
                        }
                    }
                }
            }
        }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Enter Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = showPassword,
                onCheckedChange = onShowPasswordChange
            )
            Text("Show Password")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onLoginClick) {
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = securityAnswer,
            onValueChange = onSecurityAnswerChange,
            label = { Text(securityQuestion) },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = { Text("New Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = showPassword,
                onCheckedChange = onShowPasswordChange
            )
            Text("Show Password")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onPasswordResetClick) {
            Text("Reset Password")
        }
    }
}

@Composable
fun FilesListScreen(
    filesList: List<File>,
    password: String,
    onFileDecrypt: (File) -> Unit,
    onFileDelete: (File) -> Unit
) {
    Column {
        Text("Stored Files:", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(8.dp))
        filesList.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(file.name)
                Row {
                    Button(onClick = { onFileDecrypt(file) }) {
                        Text("Unlock & Download")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onFileDelete(file) }) {
                        Text("Delete")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
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
        Button(onClick = {
            if (username.isNotEmpty() && password.isNotEmpty() && securityQuestion.isNotEmpty() && securityAnswer.isNotEmpty()) {
                val user = User(username, hashPassword(password), securityQuestion, securityAnswer)
                saveUser(user)
                onSignUp(user)
            }
        }) {
            Text("Sign Up")
        }
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