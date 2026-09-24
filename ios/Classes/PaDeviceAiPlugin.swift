import Flutter

import UIKit

import FoundationModels

 

public class PaDeviceAiPlugin: NSObject, FlutterPlugin {

 

   private static let channelName = "ai.pa/pa_device_ai"

   private let providerName = "apple_foundation_models"

 

   public static func register(with registrar: FlutterPluginRegistrar) {

       let channel = FlutterMethodChannel(

           name: channelName,

           binaryMessenger: registrar.messenger()

       )

 

       let instance = PaDeviceAiPlugin()

 

       registrar.addMethodCallDelegate(

           instance,

           channel: channel

       )

   }

 

   public func handle(

       _ call: FlutterMethodCall,

       result: @escaping FlutterResult

   ) {

       switch call.method {

 

       case "checkAvailability":

           checkAvailability(result: result)

 

       case "downloadModel":

           downloadModel(result: result)

 

       case "generateText":

           generateText(

               call: call,

               result: result

           )

 

       default:

           result(FlutterMethodNotImplemented)

       }

   }

 

   // MARK: - Availability

 

   private func checkAvailability(

       result: @escaping FlutterResult

   ) {

       guard #available(iOS 26.0, *) else {

           result([

               "status": "unavailable",

               "available": false,

               "provider": providerName,

               "errorCode": "ios_version_not_supported",

               "message": "Apple on-device AI requires iOS 26 or later."

           ])

           return

       }

 

       let model = SystemLanguageModel.default

 

       switch model.availability {

 

       case .available:

           result([

               "status": "available",

               "available": true,

               "provider": providerName,

               "message": "Apple on-device AI is available."

           ])

 

       case .unavailable(let reason):

           result(

               unavailableResult(reason: reason)

           )

       }

   }

 

   // MARK: - Model preparation

 

   private func downloadModel(

       result: @escaping FlutterResult

   ) {

       guard #available(iOS 26.0, *) else {

           result([

               "status": "unavailable",

               "available": false,

               "provider": providerName,

               "errorCode": "ios_version_not_supported",

               "message": "Apple on-device AI requires iOS 26 or later."

           ])

           return

       }

 

       let model = SystemLanguageModel.default

 

       switch model.availability {

 

       case .available:

           result([

               "status": "available",

               "available": true,

               "provider": providerName,

               "message": "The Apple on-device AI model is ready."

           ])

 

       case .unavailable(let reason):

           result(

               unavailableResult(reason: reason)

           )

       }

   }

 

   // MARK: - Generate text

 

   private func generateText(

       call: FlutterMethodCall,

       result: @escaping FlutterResult

   ) {

       guard #available(iOS 26.0, *) else {

           result([

               "status": "error",

               "available": false,

               "provider": providerName,

               "errorCode": "ios_version_not_supported",

               "message": "Apple on-device AI requires iOS 26 or later."

           ])

           return

       }

 

       guard

           let arguments = call.arguments as? [String: Any],

           let prompt = arguments["prompt"] as? String,

           !prompt.trimmingCharacters(

               in: .whitespacesAndNewlines

           ).isEmpty

       else {

           result([

               "status": "error",

               "available": false,

               "provider": providerName,

               "errorCode": "invalid_prompt",

               "message": "Supply a non-empty prompt."

           ])

           return

       }

 

       let model = SystemLanguageModel.default

 

       switch model.availability {

 

       case .available:

           break

 

       case .unavailable(let reason):

           result(

               unavailableResult(reason: reason)

           )

           return

       }

 

       Task { @MainActor in

           do {

               let session = LanguageModelSession(

                   model: model,

                   instructions: """

                   You are PA, a private on-device personal AI assistant.

                   Be helpful, accurate, clear and concise.

                   """

               )

 

               let response = try await session.respond(

                   to: prompt

               )

 

               result([

                   "status": "success",

                   "available": true,

                   "provider": self.providerName,

                   "text": response.content

               ])

 

           } catch {

               result([

                   "status": "error",

                   "available": true,

                   "provider": self.providerName,

                   "errorCode": "generation_failed",

                   "message":

                       "The on-device AI could not complete this request."

               ])

           }

       }

   }

 

   // MARK: - Availability helper

 

   @available(iOS 26.0, *)

   private func unavailableResult(

       reason: SystemLanguageModel.Availability.UnavailableReason

   ) -> [String: Any] {

       switch reason {

 

       case .deviceNotEligible:

           return [

               "status": "unavailable",

               "available": false,

               "provider": providerName,

               "errorCode": "device_not_eligible",

               "message":

                   "This device does not support Apple Intelligence."

           ]

 

       case .appleIntelligenceNotEnabled:

           return [

               "status": "unavailable",

               "available": false,

               "provider": providerName,

               "errorCode": "apple_intelligence_not_enabled",

               "message":

                   "Apple Intelligence is not enabled."

           ]

 

       case .modelNotReady:

           return [

               "status": "downloading",

               "available": false,

               "provider": providerName,

               "errorCode": "model_not_ready",

               "message":

                   "The Apple on-device AI model is not ready yet."

           ]

 

       @unknown default:

           return [

               "status": "unavailable",

               "available": false,

               "provider": providerName,

               "errorCode": "unknown_availability",

               "message":

                   "Apple on-device AI is currently unavailable."

           ]

       }

   }

}

