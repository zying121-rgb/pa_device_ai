Pod::Spec.new do |s|

 s.name             = 'pa_device_ai'

 s.version          = '0.1.0'

 s.summary          = 'Private on-device AI adapter for Android and iOS.'

 s.description      = <<-DESC

Private on-device AI adapter providing Gemini Nano support on Android

and Apple Foundation Models support on iOS.

                      DESC

 

 s.homepage         = 'https://github.com/zying121-rgb/pa_device_ai'

 s.license          = { :type => 'MIT' }

 s.author           = { 'PA' => 'support@example.com' }

 

 s.source           = {

   :git => 'https://github.com/zying121-rgb/pa_device_ai.git',

   :tag => s.version.to_s

 }

 

 s.source_files     = 'Classes/**/*'

 s.dependency 'Flutter'

 

 s.platform = :ios, '13.0'

 

 s.swift_version = '5.0'

 

 s.pod_target_xcconfig = {

   'DEFINES_MODULE' => 'YES'

 }

end
