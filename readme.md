# Secure File Storage Application

## Overview

This application is a simple file encryption and decryption tool designed to enable users to store files securely on their local machine. It utilizes a password-based encryption mechanism leveraging the AES algorithm.

## Features

- **Sign up:** Users can sign up by providing a username, password, security question, and security answer. User passwords are hashed using the SHA-256 algorithm and stored in a file named `user.dat`.
  
- **Login:** Users can log in using their username and password. The application verifies the user's identity by checking the password hash stored in the `user.dat` file. If the user enters the wrong password three times, they are prompted to reset their password by answering the security question and setting a new password.
  
- **File upload:** After logging in, users can upload files to the application. The files are encrypted using the user's password and stored in a directory named `MySecureFiles` in the user's home directory.
  
- **File decryption:** Users can decrypt and download encrypted files by entering their password. The application decrypts the files using the user's password and saves the decrypted files in the same directory as the encrypted files.
  
- **File deletion:** Users can delete encrypted files from the application. The application prompts the user to confirm the deletion before removing the file.

## Technology Stack

- Kotlin
- Jetpack Compose for Desktop (UI toolkit)
- Swing components (for file selection and confirmation dialogs)

## How to Run

To run the application:

1. Create a new Kotlin file named `Main.kt`.
2. Copy the provided code into the `Main.kt` file.
3. Execute the application using the `main` function at the end of the file.

## Conclusion

In this project, we explored building a file encryption and decryption application in Kotlin using the AES algorithm. We covered concepts such as symmetric encryption, password-based encryption, and key derivation functions. Additionally, we implemented a simple desktop application using Jetpack Compose for Desktop to showcase file encryption and decryption functionalities.

The application offers a secure means of storing files on the local machine by encrypting them with the user's password. Users can securely upload, download, and delete files using the application.

## Resources

To learn more about encryption and security in Kotlin, check out the following resources:

- [Kotlin Cryptography](https://github.com/Kotlin/kotlinx.coroutines): A comprehensive guide to cryptography in Kotlin.

