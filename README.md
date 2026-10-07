# SwipeIt 👀📱

**Control short-form videos using your head movements and eye blinks.**

SwipeIt is an experimental computer-vision-based mobile app that lets you interact with short-form video content **without constantly touching the screen**.

Using the device camera, SwipeIt detects **head movements and eye blinks** and translates them into actions such as swiping, scrolling, and liking videos.

---

## ✨ Features

### 🧠 Head Movement Controls

Swipe through videos using your head movements.

* ⬆️ **Move head up** → Swipe Up
* ⬇️ **Move head down** → Swipe Down
* ⬅️ **Move head left** → Swipe Left
* ➡️ **Move head right** → Swipe Right

### 👁️ Eye Blink Controls

Use eye blinks to interact with videos.

* 👁️ **Single Blink** → Scroll to the next reel/video
* 👁️👁️ **Double Blink** → Like the current video

The goal is to make scrolling through short-form content more natural and hands-free.

---

## 🎯 How It Works

SwipeIt uses the device's camera to continuously analyze facial movements.

The detection pipeline identifies:

```text
Camera
   ↓
Face Detection
   ↓
Head Movement / Eye Detection
   ↓
Gesture Recognition
   ↓
User Action
```

For example:

```text
Head moves up
      ↓
Gesture detected
      ↓
Swipe Up
      ↓
Next video
```

And:

```text
Single Blink
      ↓
Blink detected
      ↓
Scroll

Double Blink
      ↓
Blink pattern detected
      ↓
Like video
```

---

## 🚀 Why SwipeIt?

Short-form video platforms are designed around touch interaction, but there are situations where hands-free interaction can be useful.

SwipeIt explores how **computer vision + gesture recognition** can create a different way of interacting with mobile applications.

Instead of tapping buttons:

> **Move your head. Blink your eyes. Control your feed.**

---

## 🛠️ Technologies

The project uses technologies for:

* 📷 Camera-based vision
* 👤 Face detection
* 👀 Eye/blink detection
* 🧠 Gesture recognition
* 📱 Mobile UI
* 👆 Automated touch/swipe interactions

---

## 🎥 Demo

Add your demo video or GIF here:

```text
[ SwipeIt Demo ]
```

You can upload a short demonstration to GitHub and embed it here to show the head movements and blink interactions in action.

---

## 🧩 Gesture Mapping

| Gesture      | Action      |
| ------------ | ----------- |
| Move head ↑  | Swipe Up    |
| Move head ↓  | Swipe Down  |
| Move head ←  | Swipe Left  |
| Move head →  | Swipe Right |
| Single Blink | Scroll      |
| Double Blink | Like        |

---

## 💡 Project Concept

SwipeIt was built as an exploration of **hands-free mobile interaction** using computer vision.

The project demonstrates how everyday facial movements can be converted into meaningful mobile interactions.

It combines:

**Camera → Computer Vision → Gesture Detection → Mobile Interaction**

---

## ⚠️ Important Note

SwipeIt is an experimental project and its detection accuracy can depend on factors such as:

* Lighting conditions
* Camera quality
* Face position
* Distance from the camera
* Head movement speed
* Eye visibility

For the best experience, keep your face clearly visible to the camera.

---

## 🔮 Future Improvements

Possible improvements include:

* [ ] Customizable gesture controls
* [ ] Improved blink detection
* [ ] Better head-movement calibration
* [ ] Adjustable gesture sensitivity
* [ ] More facial gestures
* [ ] Voice commands
* [ ] Additional accessibility features
* [ ] Support for more video platforms

---

## 👨‍💻 About

**SwipeIt** is a computer-vision-based experiment exploring alternative ways to interact with short-form video content.

Built to explore the possibilities of **hands-free mobile interaction**.

---

### ⭐ If you find the project interesting, consider giving it a star!
