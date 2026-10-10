!include "MUI2.nsh"
!include "FileFunc.nsh"

Name "Cocoa Notch"
OutFile "release\Cocoa-Windows-setup.exe"
InstallDir "$LOCALAPPDATA\Programs\Cocoa"
RequestExecutionLevel user

!define MUI_ABORTWARNING
!define MUI_ICON "src-tauri\icons\icon.ico"
!define MUI_UNICON "src-tauri\icons\icon.ico"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\cocoa.exe"
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "Cocoa" SecCocoa
  SetOutPath "$INSTDIR"
  File "target\release\cocoa.exe"
  File "target\release\cocoa-hook.exe"

  CreateDirectory "$SMPROGRAMS\Cocoa"
  CreateShortcut "$SMPROGRAMS\Cocoa\Cocoa.lnk" "$INSTDIR\cocoa.exe"
  CreateShortcut "$DESKTOP\Cocoa Notch.lnk" "$INSTDIR\cocoa.exe"

  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Cocoa" "DisplayName" "Cocoa Notch"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Cocoa" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Cocoa" "DisplayIcon" "$INSTDIR\cocoa.exe"
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\Cocoa Notch.lnk"
  Delete "$SMPROGRAMS\Cocoa\Cocoa.lnk"
  RMDir "$SMPROGRAMS\Cocoa"

  Delete "$INSTDIR\cocoa.exe"
  Delete "$INSTDIR\cocoa-hook.exe"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Cocoa"
SectionEnd
