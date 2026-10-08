!include "MUI2.nsh"
!include "FileFunc.nsh"

Name "Coucou Notch"
OutFile "release\Coucou-Windows-setup.exe"
InstallDir "$LOCALAPPDATA\Programs\Coucou"
RequestExecutionLevel user

!define MUI_ABORTWARNING
!define MUI_ICON "src-tauri\icons\icon.ico"
!define MUI_UNICON "src-tauri\icons\icon.ico"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\coucou.exe"
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "Coucou" SecCoucou
  SetOutPath "$INSTDIR"
  File "target\release\coucou.exe"
  File "target\release\coucou-hook.exe"

  CreateDirectory "$SMPROGRAMS\Coucou"
  CreateShortcut "$SMPROGRAMS\Coucou\Coucou.lnk" "$INSTDIR\coucou.exe"
  CreateShortcut "$DESKTOP\Coucou Notch.lnk" "$INSTDIR\coucou.exe"

  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Coucou" "DisplayName" "Coucou Notch"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Coucou" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Coucou" "DisplayIcon" "$INSTDIR\coucou.exe"
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\Coucou Notch.lnk"
  Delete "$SMPROGRAMS\Coucou\Coucou.lnk"
  RMDir "$SMPROGRAMS\Coucou"

  Delete "$INSTDIR\coucou.exe"
  Delete "$INSTDIR\coucou-hook.exe"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Coucou"
SectionEnd
