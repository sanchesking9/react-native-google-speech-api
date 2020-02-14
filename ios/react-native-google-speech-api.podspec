require "json"

package = JSON.parse(File.read(File.join(__dir__, "../package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-google-speech-api"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.author       = 'frantsyan'
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/frantsyan/react-native-google-speech-api", :tag => "v#{s.version}" }
  s.source_files = './**/*.{h,m}'
  s.requires_arc = true
  s.dependency "React"


  s.ios.deployment_target = '7.1'
  s.osx.deployment_target = '10.9'

 # Run protoc with the Objective-C and gRPC plugins to generate protocol messages and gRPC clients.
  s.dependency "!ProtoCompiler-gRPCPlugin", "~> 1.6"

  # Pods directory corresponding to this app's Podfile, relative to the location of this podspec.
  pods_root = './../../../ios/Pods'

  # Path where Cocoapods downloads protoc and the gRPC plugin.
  protoc_dir = "#{pods_root}/!ProtoCompiler"
  protoc = "#{protoc_dir}/protoc"
  plugin = "#{pods_root}/!ProtoCompiler-gRPCPlugin/grpc_objective_c_plugin"

  # Run protoc with the Objective-C and gRPC plugins to generate protocol messages and gRPC clients.
  # You can run this command manually if you later change your protos and need to regenerate.
  s.prepare_command = <<-CMD
    #{protoc} \
        --plugin=protoc-gen-grpc=#{plugin} \
        --objc_out=. \
        --grpc_out=. \
        -I . \
        -I #{protoc_dir} \
        google/*/*.proto google/*/*/*/*.proto
  CMD

    # The --objc_out plugin generates a pair of .pbobjc.h/.pbobjc.m files for each .proto file.
    s.subspec "Messages" do |ms|
      ms.source_files = "./google/**/*.pbobjc.{h,m}"
      ms.header_mappings_dir = "."
      ms.requires_arc = false
      ms.dependency "Protobuf"
    end

    # The --objcgrpc_out plugin generates a pair of .pbrpc.h/.pbrpc.m files for each .proto file with
    # a service defined.
    s.subspec "Services" do |ss|
      ss.source_files = "./google/**/*.pbrpc.{h,m}"
      ss.header_mappings_dir = "."
      ss.requires_arc = true
      ss.dependency "gRPC-ProtoRPC"
      ss.dependency "#{s.name}/Messages"
    end
end
